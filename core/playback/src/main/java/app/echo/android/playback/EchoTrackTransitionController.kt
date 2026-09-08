package app.echo.android.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.echo.android.model.playback.EchoTrackTransitionOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Service-owned scheduling from the actual playback clock; no UI ticker or second player. */
@UnstableApi
internal class EchoTrackTransitionController(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private val onGain: (Float) -> Unit,
) : Player.Listener, AutoCloseable {
    private var options = EchoPlaybackRuntimeOptionsStore.options.value.trackTransitions
    private var fadeJob: Job? = null
    private var lastGain = Float.NaN
    private val optionsJob: Job

    init {
        player.setPreloadConfiguration(ExoPlayer.PreloadConfiguration(5_000_000L))
        player.addListener(this)
        optionsJob = scope.launch {
            combine(EchoPlaybackRuntimeOptionsStore.options.map { it.trackTransitions }.distinctUntilChanged(),
                EchoPlaybackProcessRuntime.bitPerfectStates.map { EchoPlaybackProcessRuntime.usbBitPerfectEnabled }.distinctUntilChanged()) { config, strict -> config to strict }
                .collect { (config, _) -> options = config.normalized(); refresh() }
        }
        refresh()
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_PLAYBACK_PARAMETERS_CHANGED, Player.EVENT_TIMELINE_CHANGED)) refresh()
    }

    private fun refresh() {
        fadeJob?.cancel(); fadeJob = null
        val enabled = options.fadeEnabled && !EchoPlaybackProcessRuntime.usbBitPerfectEnabled && !player.isCurrentMediaItemLive
        updateGain(enabled)
        if (!enabled || !player.isPlaying) return
        fadeJob = scope.launch {
            while (isActive && player.isPlaying) {
                updateGain(true)
                val wait = EchoTrackFadePolicy.nextDelayMs(player.currentPosition, player.duration,
                    options.fadeDurationMs, player.playbackParameters.speed) ?: break
                delay(wait)
            }
        }
    }

    private fun updateGain(enabled: Boolean) {
        val gain = EchoTrackFadePolicy.gain(player.currentPosition, player.duration, options.fadeDurationMs, enabled)
        if (gain == lastGain) return
        lastGain = gain
        onGain(gain)
    }

    override fun close() {
        fadeJob?.cancel(); optionsJob.cancel()
        player.removeListener(this)
        onGain(1f)
    }
}
