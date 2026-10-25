package app.echo.android.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Only MediaSession/manual commands use this facade. Focus/noisy/timer owners retain the raw player. */
@UnstableApi
internal class EchoManualFadePlayer(private val raw: Player, private val scope: CoroutineScope,
    private val state: EchoManualFadeState = EchoPlaybackProcessRuntime.manualFadeState,
    private val canFade: () -> Boolean = { EchoPlaybackRuntimeOptionsStore.options.value.trackTransitions.playPauseFadeEnabled && !EchoPlaybackProcessRuntime.usbBitPerfectEnabled },
    enabledFlow: Flow<Boolean> = combine(EchoPlaybackRuntimeOptionsStore.options, EchoPlaybackProcessRuntime.bitPerfectStates) { config, _ ->
        config.trackTransitions.playPauseFadeEnabled && !EchoPlaybackProcessRuntime.usbBitPerfectEnabled },
    private val onGain: (Float) -> Unit = EchoPlaybackProcessRuntime::setManualFadeGain,
) : ForwardingPlayer(raw), AutoCloseable {
    private var fade: Job? = null
    private var playFadePending = false
    private var closed = false
    private var generation = 0L
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            if (playing && playFadePending) startFadeIn()
            else if (!playing && state.intent == false && raw.playWhenReady) {
                raw.pause(); cancelFade()
            }
        }
        override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) { if (!ready) cancelFade() }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            val pauseRequested = state.intent == false
            cancelFade()
            if (pauseRequested && (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)) raw.pause()
        }
        override fun onPlayerError(error: PlaybackException) { if (state.intent == false) raw.pause(); cancelFade() }
        override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_REMOVE) cancelFade()
        }
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) cancelFade()
        }
    }
    private val options = scope.launch {
        enabledFlow.distinctUntilChanged().collect { enabled ->
            if (!enabled) {
                if (state.intent == false) raw.pause()
                cancelFade()
            }
        }
    }
    init { raw.addListener(listener) }
    private fun allowed() = !closed && canFade()
    private fun gain(value: Float) { state.gain = value.coerceIn(0f, 1f); onGain(state.gain) }
    private fun cancelFade(reset: Boolean = true) {
        generation++; fade?.cancel(); fade = null; playFadePending = false
        state.intent = null
        if (reset) gain(1f)
    }
    override fun play() = setPlayWhenReady(true)
    override fun pause() = setPlayWhenReady(false)
    override fun stop() { cancelFade(); raw.stop() }
    override fun setPlayWhenReady(ready: Boolean) {
        if (closed) return
        if (!allowed() || raw.mediaItemCount == 0) { cancelFade(); raw.playWhenReady = ready; return }
        if (ready) {
            val continuing = raw.playWhenReady
            cancelFade(reset = false)
            state.intent = true
            if (continuing) { if (raw.isPlaying) startFadeIn() else playFadePending = true; return }
            gain(0f); playFadePending = true
            raw.play()
            if (playFadePending && raw.isPlaying) startFadeIn()
        } else {
            if (!raw.isPlaying) { cancelFade(); raw.pause(); return }
            if (state.intent == false) return
            cancelFade(reset = false)
            state.intent = false
            val token = generation
            val id = raw.currentMediaItem?.mediaId
            val index = raw.currentMediaItemIndex
            val from = state.gain
            fade = scope.launch {
                if (!ramp(from, 0f) {
                    EchoManualFadePolicy.sameRequest(token, generation, id, raw.currentMediaItem?.mediaId, index, raw.currentMediaItemIndex) && raw.playWhenReady
                }) return@launch

                if (EchoManualFadePolicy.sameRequest(token, generation, id, raw.currentMediaItem?.mediaId, index, raw.currentMediaItemIndex)) raw.pause()
                cancelFade()
            }
        }
    }
    fun toggle() { setPlayWhenReady(!(state.intent ?: raw.playWhenReady)) }
    private fun startFadeIn() {
        playFadePending = false
        fade?.cancel()
        val token = generation
        val from = state.gain
        fade = scope.launch {
            if (!ramp(from, 1f) { token == generation && raw.playWhenReady }) return@launch
            cancelFade()
        }
    }
    private suspend fun ramp(from: Float, to: Float, valid: () -> Boolean): Boolean {
        val start = System.nanoTime()
        while (true) {
            delay(DurationMs / Steps)
            if (!valid()) return false
            val progress = ((System.nanoTime() - start) / (DurationMs * 1_000_000.0)).toFloat().coerceIn(0f, 1f)
            gain(from + (to - from) * progress)
            if (progress >= 1f) return true
        }
    }
    override fun close() { closed = true; options.cancel(); cancelFade(); raw.removeListener(listener) }
    companion object { const val DurationMs = 180L; private const val Steps = 12 }
}

internal object EchoManualFadePolicy {
    fun shouldRememberResume(playWhenReady: Boolean, manualIntent: Boolean?) = playWhenReady && manualIntent != false
    fun sameRequest(expected: Long, actual: Long, expectedId: String?, actualId: String?, expectedIndex: Int, actualIndex: Int) =
        expected == actual && expectedId == actualId && expectedIndex == actualIndex
}

internal class EchoManualFadeState {
    @Volatile var intent: Boolean? = null
    @Volatile var gain: Float = 1f
}
