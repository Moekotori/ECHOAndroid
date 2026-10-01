package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.echo.android.model.playback.EchoSleepTimerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

/** One service-owned plan; player commands stay on its application thread. */
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
    private val optionsJob: Job
    private var armed: Armed? = null
    private var committing = false

    init {
        player.addListener(this)
        optionsJob = scope.launch {
            combine(EchoPlaybackRuntimeOptionsStore.options,
                EchoPlaybackProcessRuntime.bitPerfectStates,
                EchoPlaybackCachePolicy.transitionModes,
                EchoPlaybackProcessRuntime.sleepTransitionModes,
                EchoPlaybackProcessRuntime.abLoop) { _, _, _, _, _ -> Unit }
                .collect { refresh() }
        }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_PLAYBACK_PARAMETERS_CHANGED, Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED)) refresh()
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (!committing && (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_REMOVE)) {
            EchoPlaybackProcessRuntime.smartFadeInMediaId = null
            refresh()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (committing) return
        val handoff = armed
        EchoPlaybackProcessRuntime.smartFadeInMediaId = null
        val continuation = handoff?.let {
            EchoSmartTransitionHandoff.continuationMs(
                automatic = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
                expectedIndex = it.nextIndex, actualIndex = player.currentMediaItemIndex,
                expectedId = it.nextId, actualId = mediaItem?.mediaId,
                startMs = it.startMs, mixedFrames = it.mix.mixedFrames,
                sampleRateHz = it.mix.sampleRateHz,
            )
        }
        disarm()
        if (continuation != null && continuation > player.currentPosition) {
            // Keep the original MediaItem/timeline: no persistent clipping, duration or lyric offset.
            EchoPlaybackProcessRuntime.smartFadeInMediaId = mediaItem?.mediaId
            EchoPlaybackProcessRuntime.setTrackFadeGain(1f)
            committing = true
            try { player.seekTo(player.currentMediaItemIndex, continuation) }
            finally { committing = false }
        }
    }

    private fun refresh() {
        loopJob?.cancel()
        loopJob = null
        disarm()
        val enabled = !EchoPlaybackProcessRuntime.abLoop.value.active && EchoSmartTransitionPolicy.mixerPassthroughEnabled(
            EchoPlaybackRuntimeOptionsStore.options.value.trackTransitions,
            EchoPlaybackCachePolicy.effectiveMode, EchoPlaybackProcessRuntime.usbExclusiveEnabled,
            EchoPlaybackProcessRuntime.usbBitPerfectEnabled)
        mixer.setEnabled(enabled)
        if (!enabled) return
        val snapshot = snapshot() ?: return
        if (EchoSmartTransitionPolicy.bypassReason(snapshot.candidate) != null) return
        loopJob = scope.launch { prepare(snapshot) }
    }

    private suspend fun prepare(snapshot: LoopSnapshot) {
        val lead = EchoSmartTransitionPolicy.analyzeLeadMs(snapshot.candidate.performanceMode)
        if (lead != Long.MAX_VALUE) delay((remainingMs(snapshot) - lead).coerceAtLeast(0))
        if (!stillCurrent(snapshot)) return
        val nextAnalysis = decodeMutex.withLock {
            analyzer.analysisFor(snapshot.nextId, snapshot.nextUri, snapshot.candidate.nextDurationMs, true, false)
        } ?: return
        delay((remainingMs(snapshot) - EchoSmartTransitionPolicy.AnalyzeLeadMs).coerceAtLeast(0))
        if (!stillCurrent(snapshot)) return
        val currentAnalysis = decodeMutex.withLock {
            analyzer.analysisFor(snapshot.currentId, snapshot.currentUri, snapshot.candidate.currentDurationMs, false, true)
        } ?: return
        if (!currentAnalysis.hasOutro || !nextAnalysis.hasIntro || !stillCurrent(snapshot)) return
        val plan = EchoSmartTransitionPlanner.plan(currentAnalysis, nextAnalysis, remainingMs(snapshot),
            snapshot.candidate.nextDurationMs, EchoSmartTransitionPolicy.maxOverlapMs(snapshot.candidate),
            EchoPlaybackProcessRuntime.replayGainDb(snapshot.currentId).takeIf { EchoPlaybackProcessRuntime.replayGainEnabled },
            EchoPlaybackProcessRuntime.replayGainDb(snapshot.nextId).takeIf { EchoPlaybackProcessRuntime.replayGainEnabled }) ?: return
        delay((remainingMs(snapshot) - plan.overlapMs - EchoSmartTransitionPolicy.PredecodeLeadMs).coerceAtLeast(0))
        if (!stillCurrent(snapshot)) return
        val rate = mixer.outputSampleRateHz ?: return
        val channels = mixer.outputChannelCount
        val epoch = mixer.streamEpoch
        val pcm = decodeMutex.withLock {
            decoder.decodeMixWindow(snapshot.nextUri, plan.nextStartMs.toLong(), plan.overlapMs.toLong(), rate, channels)
        } ?: return
        coroutineContext.ensureActive()
        if (!stillCurrent(snapshot) || mixer.streamEpoch != epoch || mixer.outputSampleRateHz != rate) return
        val startPosition = snapshot.candidate.currentDurationMs - plan.overlapMs
        // Do not start late or truncate a prepared window. PCM position includes sink prebuffering.
        if (mixer.processedPositionUs >= startPosition * 1000L) return
        val mix = mixer.arm(pcm, pcm.size / channels, channels,
            incomingGain = plan.incomingGain, bassSwap = plan.bassSwap,
            startPositionMs = startPosition, expectedEpoch = epoch) ?: return
        armed = Armed(snapshot.nextIndex, snapshot.nextId, plan.nextStartMs.toLong(), mix)
        EchoPlaybackProcessRuntime.setSmartMixArmed(true)
        // Any player/environment event invalidates the plan; no idle polling.
        awaitCancellation()
    }

    private fun stillCurrent(previous: LoopSnapshot): Boolean {
        val current = snapshot() ?: return false
        return current.currentIndex == previous.currentIndex && current.nextIndex == previous.nextIndex &&
            current.currentId == previous.currentId && current.nextId == previous.nextId &&
            current.currentUri == previous.currentUri && current.nextUri == previous.nextUri &&
            EchoSmartTransitionPolicy.bypassReason(current.candidate) == null
    }

    private fun remainingMs(snapshot: LoopSnapshot): Long =
        (snapshot.candidate.currentDurationMs - player.currentPosition).coerceAtLeast(0)

    private fun snapshot(): LoopSnapshot? {
        val currentItem = player.currentMediaItem ?: return null
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return null
        val nextItem = player.getMediaItemAt(nextIndex)
        // CUE/source ranges need their own analysis coordinates; preserve their original playback.
        if (currentItem.clippingConfiguration != MediaItem.ClippingConfiguration.UNSET ||
            nextItem.clippingConfiguration != MediaItem.ClippingConfiguration.UNSET) return null
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
            currentIndex = player.currentMediaItemIndex,
            nextIndex = nextIndex,
            currentId = current.id,
            nextId = next.id,
            currentUri = currentUri,
            nextUri = nextUri,
            candidate = candidate,
        )
    }

    private fun disarm() {
        mixer.cancel()
        armed = null
        EchoPlaybackProcessRuntime.setSmartMixArmed(false)
    }

    override fun close() {
        loopJob?.cancel()
        optionsJob.cancel()
        player.removeListener(this)
        disarm()
        EchoPlaybackProcessRuntime.smartFadeInMediaId = null
        mixer.setEnabled(false)
    }

    private data class Armed(val nextIndex: Int, val nextId: String, val startMs: Long,
        val mix: EchoSmartTransitionMixer.MixSession)

    private data class LoopSnapshot(
        val currentIndex: Int,
        val nextIndex: Int,
        val currentId: String,
        val nextId: String,
        val currentUri: String,
        val nextUri: String,
        val candidate: EchoSmartTransitionPolicy.Candidate,
    )
}
