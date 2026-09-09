package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.echo.android.model.playback.EchoSleepTimerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

@UnstableApi
internal class EchoSmartTransitionController(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private val mixer: EchoSmartTransitionMixer,
    private val analyzer: EchoSmartTransitionAnalyzer,
    private val decoder: EchoSmartTransitionDecoder,
) : Player.Listener, AutoCloseable {
    private val decodeMutex = Mutex()
    private var loopJob: Job? = null
    private var optionsJob: Job
    private var loopKey: String? = null
    private var commitPositionMs: Long = 0L
    private var armedToId: String? = null
    private var clippedNextIndex: Int = C.INDEX_UNSET
    private var unclippedNextItem: MediaItem? = null
    private var usedClipping: Boolean = false
    @Volatile private var committing: Boolean = false

    init {
        player.addListener(this)
        optionsJob = scope.launch {
            combine(
                EchoPlaybackRuntimeOptionsStore.options,
                EchoPlaybackProcessRuntime.bitPerfectStates,
            ) { _, _ -> }
                .collect { refresh(force = true) }
        }
        refresh(force = true)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
            )
        ) {
            refresh(force = false)
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (committing) return
        if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_REMOVE) {
            refresh(force = true)
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val incomingId = mediaItem?.mediaId
        if (incomingId != null && incomingId == armedToId && !usedClipping && commitPositionMs > 0L) {
            if (player.currentPosition + 50L < commitPositionMs) {
                committing = true
                player.seekTo(player.currentMediaItemIndex, commitPositionMs)
                committing = false
            }
        }
        disarm(restoreClip = false)
    }

    private fun refresh(force: Boolean) {
        val passthrough = EchoSmartTransitionPolicy.mixerPassthroughEnabled(
            options = EchoPlaybackRuntimeOptionsStore.options.value.trackTransitions,
            mode = EchoPlaybackCachePolicy.effectiveMode,
            usbExclusive = EchoPlaybackProcessRuntime.usbExclusiveEnabled,
            usbBitPerfect = EchoPlaybackProcessRuntime.usbBitPerfectEnabled,
        )
        if (mixer.setEnabled(passthrough)) {
            EchoPlaybackProcessRuntime.reconfigureAudioPipeline()
        }
        val key = loopIdentity()
        if (!force && key == loopKey && loopJob?.isActive == true) return
        loopKey = key
        loopJob?.cancel()
        if (!passthrough) {
            disarm()
            return
        }
        loopJob = scope.launch { runLoop() }
    }

    private fun loopIdentity(): String {
        val current = player.currentMediaItem?.mediaId.orEmpty()
        val nextIndex = player.nextMediaItemIndex
        val next = if (nextIndex == C.INDEX_UNSET) "" else player.getMediaItemAt(nextIndex).mediaId
        val options = EchoPlaybackRuntimeOptionsStore.options.value
        return listOf(
            current,
            next,
            options.trackTransitions.smartEnabled,
            options.trackTransitions.fadeEnabled,
            options.trackTransitions.fadeDurationMs,
            options.skipSilenceEnabled,
            EchoPlaybackCachePolicy.effectiveMode.id,
            EchoPlaybackProcessRuntime.usbExclusiveEnabled,
            EchoPlaybackProcessRuntime.usbBitPerfectEnabled,
            player.repeatMode,
            player.playbackParameters.speed,
            EchoPlaybackProcessRuntime.sleepTimerMode,
        ).joinToString("|")
    }

    private suspend fun runLoop() {
        while (coroutineContext.isActive) {
            val snapshot = snapshot() ?: run {
                disarm()
                delay(500)
                continue
            }
            val reason = EchoSmartTransitionPolicy.bypassReason(snapshot.candidate)
            if (reason != null) {
                disarm()
                delay(400)
                continue
            }
            val remaining = remainingMs(snapshot.candidate.currentDurationMs)
            val analyzeLead = EchoSmartTransitionPolicy.analyzeLeadMs(snapshot.candidate.performanceMode)
            if (remaining > analyzeLead && analyzeLead != Long.MAX_VALUE) {
                delay((remaining - analyzeLead).coerceAtLeast(200L))
                continue
            }
            val nextAnalysis = decodeMutex.withLock {
                analyzer.analysisFor(
                    snapshot.nextId,
                    snapshot.nextUri,
                    snapshot.candidate.nextDurationMs,
                    needIntro = true,
                    needOutro = false,
                )
            }
            val currentLead = EchoSmartTransitionPolicy.AnalyzeLeadMs
            if (remainingMs(snapshot.candidate.currentDurationMs) > currentLead) {
                delay((remainingMs(snapshot.candidate.currentDurationMs) - currentLead).coerceAtLeast(50L))
            }
            if (player.currentMediaItem?.mediaId != snapshot.currentId) continue
            val currentAnalysis = decodeMutex.withLock {
                analyzer.analysisFor(
                    snapshot.currentId,
                    snapshot.currentUri,
                    snapshot.candidate.currentDurationMs,
                    needIntro = false,
                    needOutro = true,
                )
            }
            if (currentAnalysis == null || nextAnalysis == null) {
                disarm()
                return
            }
            val outputRate = mixer.outputSampleRateHz
                ?: snapshot.candidate.currentSampleRateHz
                ?: currentAnalysis.sampleRateHz
            val nextRate = nextAnalysis.sampleRateHz ?: snapshot.candidate.nextSampleRateHz
            if (outputRate == null || nextRate == null || outputRate != nextRate) {
                disarm()
                return
            }
            val maxOverlap = EchoSmartTransitionPolicy.maxOverlapMs(snapshot.candidate)
            fun planned(): EchoSmartTransitionPlan? = EchoSmartTransitionPlanner.plan(
                current = currentAnalysis,
                next = nextAnalysis,
                remainingMs = remainingMs(snapshot.candidate.currentDurationMs),
                nextDurationMs = snapshot.candidate.nextDurationMs,
                maxOverlapMs = maxOverlap,
                currentReplayGainDb = EchoPlaybackProcessRuntime.replayGainDb(snapshot.currentId),
                nextReplayGainDb = EchoPlaybackProcessRuntime.replayGainDb(snapshot.nextId),
            )
            var plan = planned()
            if (plan == null) {
                disarm()
                return
            }
            val predecodeAt = plan.overlapMs + EchoSmartTransitionPolicy.PredecodeLeadMs
            while (coroutineContext.isActive &&
                remainingMs(snapshot.candidate.currentDurationMs - plan.currentEndTrimMs) > predecodeAt + 50L
            ) {
                delay(50)
            }
            if (!coroutineContext.isActive) return
            if (player.currentMediaItem?.mediaId != snapshot.currentId) continue
            plan = planned()
            if (plan == null) {
                disarm()
                return
            }
            val channels = mixer.outputChannelCount.coerceIn(1, 2)
            val pcm = decodeMutex.withLock {
                decoder.decodeMixWindow(
                    uri = snapshot.nextUri,
                    startMs = plan.nextStartMs.toLong(),
                    durationMs = plan.overlapMs.toLong(),
                    expectedRateHz = outputRate,
                    expectedChannels = channels,
                )
            }
            if (pcm == null || !coroutineContext.isActive) {
                disarm()
                return
            }
            val frames = pcm.size / channels
            if (frames <= 0) {
                disarm()
                return
            }
            val hold = EchoSmartTransitionPolicy.holdFrames(
                remainingMs = remainingMs(snapshot.candidate.currentDurationMs - plan.currentEndTrimMs),
                overlapMs = plan.overlapMs,
                sampleRateHz = outputRate,
            )
            armedToId = snapshot.nextId
            commitPositionMs = plan.nextStartMs.toLong() + plan.overlapMs
            clipNext(snapshot.nextId, commitPositionMs)
            mixer.arm(
                pcm = pcm,
                frames = frames,
                channels = channels,
                holdFrames = hold,
                incomingGain = plan.incomingGain,
                bassSwap = plan.bassSwap,
            )
            EchoPlaybackProcessRuntime.setSmartMixArmed(true)
            val fromId = snapshot.currentId
            while (coroutineContext.isActive && player.currentMediaItem?.mediaId == fromId) {
                delay(200)
            }
        }
    }

    private fun remainingMs(durationMs: Long): Long =
        (durationMs - player.currentPosition.coerceAtLeast(0L)).coerceAtLeast(0L)

    private fun clipNext(nextId: String, startMs: Long) {
        restoreClip()
        if (startMs <= 0L) return
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return
        val item = player.getMediaItemAt(nextIndex)
        if (item.mediaId != nextId) return
        unclippedNextItem = item
        clippedNextIndex = nextIndex
        usedClipping = true
        player.replaceMediaItem(
            nextIndex,
            item.buildUpon()
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .build(),
                )
                .build(),
        )
    }

    private fun restoreClip() {
        val index = clippedNextIndex
        val original = unclippedNextItem
        clippedNextIndex = C.INDEX_UNSET
        unclippedNextItem = null
        usedClipping = false
        if (index == C.INDEX_UNSET || original == null) return
        if (index in 0 until player.mediaItemCount && player.getMediaItemAt(index).mediaId == original.mediaId) {
            player.replaceMediaItem(index, original)
        }
    }

    private fun snapshot(): LoopSnapshot? {
        val currentItem = player.currentMediaItem ?: return null
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return null
        val nextItem = player.getMediaItemAt(nextIndex)
        val current = currentItem.toEchoTrackRef(player.duration.takeIf { it > 0L } ?: 0L)
        val nextDuration = nextItem.mediaMetadata.durationMs?.takeIf { it > 0L }
            ?: nextItem.toEchoTrackRef().durationMs
        val next = nextItem.toEchoTrackRef(nextDuration)
        val currentUri = currentItem.localConfiguration?.uri?.toString() ?: current.uri
        val nextUri = nextItem.localConfiguration?.uri?.toString() ?: next.uri
        val candidate = EchoSmartTransitionPolicy.Candidate(
            options = EchoPlaybackRuntimeOptionsStore.options.value.trackTransitions,
            performanceMode = EchoPlaybackCachePolicy.effectiveMode,
            usbExclusive = EchoPlaybackProcessRuntime.usbExclusiveEnabled,
            usbBitPerfect = EchoPlaybackProcessRuntime.usbBitPerfectEnabled,
            live = player.isCurrentMediaItemLive,
            currentDurationMs = player.duration.takeIf { it > 0L } ?: current.durationMs,
            currentPositionMs = player.currentPosition.coerceAtLeast(0L),
            nextDurationMs = next.durationMs,
            playbackSpeed = player.playbackParameters.speed,
            skipSilence = EchoPlaybackRuntimeOptionsStore.options.value.skipSilenceEnabled,
            repeatOne = player.repeatMode == Player.REPEAT_MODE_ONE,
            sleepEndOfTrack = EchoPlaybackProcessRuntime.sleepTimerMode == EchoSleepTimerMode.EndOfTrack,
            currentLocal = EchoSmartTransitionPolicy.isLocalUri(currentUri, current.sourceId),
            nextLocal = EchoSmartTransitionPolicy.isLocalUri(nextUri, next.sourceId),
            currentAlbum = current.album,
            nextAlbum = next.album,
            currentDisc = current.discNumber,
            nextDisc = next.discNumber,
            currentTrackNumber = current.trackNumber,
            nextTrackNumber = next.trackNumber,
            currentSampleRateHz = current.sampleRateHz,
            nextSampleRateHz = next.sampleRateHz,
            outputSampleRateHz = mixer.outputSampleRateHz ?: current.sampleRateHz,
            outputChannelCount = mixer.outputChannelCount,
            hasNext = true,
            isPlaying = player.isPlaying,
        )
        return LoopSnapshot(
            currentId = current.id,
            nextId = next.id,
            currentUri = currentUri,
            nextUri = nextUri,
            candidate = candidate,
        )
    }

    private fun disarm(restoreClip: Boolean = true) {
        mixer.cancel()
        if (restoreClip) restoreClip() else {
            clippedNextIndex = C.INDEX_UNSET
            unclippedNextItem = null
            usedClipping = false
        }
        armedToId = null
        commitPositionMs = 0L
        EchoPlaybackProcessRuntime.setSmartMixArmed(false)
    }

    override fun close() {
        loopJob?.cancel()
        optionsJob.cancel()
        player.removeListener(this)
        disarm()
        mixer.setEnabled(false)
    }

    private data class LoopSnapshot(
        val currentId: String,
        val nextId: String,
        val currentUri: String,
        val nextUri: String,
        val candidate: EchoSmartTransitionPolicy.Candidate,
    )
}
