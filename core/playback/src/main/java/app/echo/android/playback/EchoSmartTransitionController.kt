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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/** One service-owned plan; player commands stay on its application thread. */
@UnstableApi
internal class EchoSmartTransitionController(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private val mixer: EchoSmartTransitionMixer,
    private val autoMix: EchoAutoMixEngine,
    private val decoder: EchoSmartTransitionDecoder,
    private val onAutomaticAdvance: (Int, Int) -> Unit = { _, _ -> },
) : Player.Listener, AutoCloseable {
    private val decodeMutex = Mutex()
    private var loopJob: Job? = null
    private val optionsJob: Job
    private var armed: Armed? = null
    private var committing = false
    private val playing = MutableStateFlow(player.isPlaying)

    init {
        player.addListener(this)
        optionsJob = scope.launch {
            combine(EchoPlaybackRuntimeOptionsStore.options.map { it.trackTransitions }.distinctUntilChanged(),
                EchoPlaybackProcessRuntime.bitPerfectStates,
                EchoPlaybackCachePolicy.transitionModes,
                EchoPlaybackProcessRuntime.sleepTransitionModes,
                EchoPlaybackProcessRuntime.abLoop) { _, _, _, _, _ -> }
                .collect { refresh() }
        }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        playing.value = player.isPlaying
        if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_PLAYBACK_PARAMETERS_CHANGED, Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED)) {
            val structuralChange = events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)
            refresh(preserveArmed = !structuralChange &&
                player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED)
        }
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

    private fun refresh(preserveArmed: Boolean = false) {
        if (committing) return
        if (preserveArmed) {
            val active = armed
            val current = snapshot()
            if (active != null && current != null && active.nextIndex == current.nextIndex &&
                active.nextId == current.nextId && EchoSmartTransitionHandoff.retainArmed(
                    EchoSmartTransitionPolicy.bypassReason(current.candidate),
                    active.mix.epoch == mixer.streamEpoch)) return
        }
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
        if (!stillCurrent(snapshot)) return
        // Enabling the option can precede the audio thread's configure/flush by a buffer.
        val stream = withTimeoutOrNull(1_500) { mixer.streamFormats.filterNotNull().first() } ?: return
        if (!stillCurrent(snapshot)) return
        val rate = stream.rate
        val channels = stream.channels
        val epoch = stream.epoch
        val plan = decodeMutex.withLock {
            autoMix.prepare(snapshot.currentUri, snapshot.nextUri, snapshot.candidate.currentDurationMs,
                snapshot.candidate.nextDurationMs, rate, channels,
                EchoSmartTransitionPolicy.maxOverlapMs(snapshot.candidate))
        } ?: return
        if (!stillCurrent(snapshot) || plan.startMs <= player.currentPosition) return
        delay((plan.startMs - player.currentPosition - EchoSmartTransitionPolicy.PredecodeLeadMs).coerceAtLeast(0))
        if (!stillCurrent(snapshot)) return
        val pcm = decodeMutex.withLock {
            val from = decoder.decodeMixWindow(snapshot.currentUri, plan.startMs, plan.durationMs, rate, channels)
                ?: return@withLock null
            val to = decoder.decodeMixWindow(snapshot.nextUri, plan.nextStartMs, plan.durationMs, rate, channels)
                ?: return@withLock null
            autoMix.render(plan, from, to, channels)
        } ?: return
        coroutineContext.ensureActive()
        if (!stillCurrent(snapshot) || mixer.streamEpoch != epoch || mixer.outputSampleRateHz != rate) return
        val startPosition = plan.startMs
        // Do not start late or truncate a prepared window. PCM position includes sink prebuffering.
        if (mixer.processedPositionUs >= startPosition * 1000L) return
        val mix = mixer.arm(pcm, pcm.size / channels, channels,
            startPositionMs = startPosition, expectedEpoch = epoch, rendered = true) ?: return
        armed = Armed(snapshot.nextIndex, snapshot.nextId, plan.nextStartMs, mix)
        try {
            EchoPlaybackProcessRuntime.publishSmartTransitionVisual(app.echo.android.model.playback.EchoSmartTransitionVisual(
                snapshot.currentId, player.getMediaItemAt(snapshot.nextIndex).toEchoTrackRef(),
                plan.startMs, plan.durationMs))
            EchoPlaybackProcessRuntime.setSmartMixArmed(true)
            val endPosition = startPosition + plan.durationMs
            if (endPosition < snapshot.candidate.currentDurationMs - 2) {
                // Wait on the audible player clock, never the processor's prebuffered cursor.
                while (stillCurrent(snapshot) && player.currentPosition < endPosition) {
                    // Pauses/buffering retain the exact PCM cursor without background polling.
                    playing.first { it }
                    if (!stillCurrent(snapshot)) return
                    delay((endPosition - player.currentPosition - 100).coerceAtLeast(20))
                }
                if (!stillCurrent(snapshot) || mix.mixedFrames != mix.frames) return
                val continuation = plan.nextStartMs + mix.frames.toLong() * 1000L / rate
                EchoPlaybackProcessRuntime.smartFadeInMediaId = snapshot.nextId
                EchoPlaybackProcessRuntime.setTrackFadeGain(1f)
                committing = true
                try {
                    onAutomaticAdvance(snapshot.currentIndex, snapshot.nextIndex)
                    player.seekTo(snapshot.nextIndex, continuation)
                }
                finally { committing = false }
                refresh()
                return
            }
            // Any player/environment event invalidates the plan; no idle polling.
            awaitCancellation()
        } finally {
            // A cancelled/invalidated worker must not leave presentation or fade suppression armed.
            // Refresh may already have installed a newer session while this worker unwinds.
            if (armed?.mix === mix) disarm()
        }
    }

    private fun stillCurrent(previous: LoopSnapshot): Boolean {
        val current = snapshot() ?: return false
        return current.currentIndex == previous.currentIndex && current.nextIndex == previous.nextIndex &&
            current.currentId == previous.currentId && current.nextId == previous.nextId &&
            current.currentUri == previous.currentUri && current.nextUri == previous.nextUri &&
            (EchoSmartTransitionPolicy.bypassReason(current.candidate).let { reason ->
                val active = armed
                if (active == null) reason == null else
                    EchoSmartTransitionHandoff.retainArmed(reason, active.mix.epoch == mixer.streamEpoch)
            })
    }

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
            hasNext = nextIndex != player.currentMediaItemIndex,
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
        EchoPlaybackProcessRuntime.publishSmartTransitionVisual(null)
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
