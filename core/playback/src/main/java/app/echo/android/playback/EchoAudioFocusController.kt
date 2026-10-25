package app.echo.android.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.EchoAudioInterruptionPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Service/application-looper owner. Media3's automatic focus handler is disabled for this player. */
@UnstableApi
internal class EchoAudioFocusController(
    context: Context,
    private val player: Player,
    private val scope: CoroutineScope,
    private val canInteract: () -> Boolean,
) : Player.Listener, AutoCloseable {
    private val manager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val route = EchoPlaybackProcessRuntime.outputRouteMonitor(context.applicationContext).route
    private val resume = EchoAudioResumeGuard()
    private var acting = false
    private var held = false
    private var ducked = false
    private var closed = false
    private var optionsJob: Job? = null
    private var routeJob: Job? = null
    private var observedRoute = routeKey()
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setAcceptsDelayedFocusGain(true)
        // Handle CAN_DUCK ourselves so the selected policy also works on native USB output.
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change -> onFocus(change) }, Handler(Looper.getMainLooper()))
        .build()

    init {
        player.addListener(this)
        optionsJob = scope.launch {
            combine(EchoPlaybackRuntimeOptionsStore.options, EchoPlaybackProcessRuntime.bitPerfectStates) { options, _ ->
                options.audioInterruptionPolicy to EchoPlaybackProcessRuntime.usbBitPerfectEnabled
            }.distinctUntilChanged().collect { (policy, strict) ->
                if (ducked && (strict || policy == EchoAudioInterruptionPolicy.Pause)) pauseForFocus()
            }
        }
        routeJob = scope.launch {
            route.collect {
                val key = routeKey()
                if (key != observedRoute) { observedRoute = key; resume.cancel() }
            }
        }
        if (player.playWhenReady) requestFocus()
    }

    fun onUserCommand(command: Int) {
        if (command in UserIntentCommands) {
            resume.cancel() // Also catches a second pause while focus already has the player paused.
            EchoPlaybackProcessRuntime.onSleepTimerUserCommand()
        }
    }

    private fun routeKey(): String = route.value.let { "${it.kind}|${it.deviceName}|${it.deviceNames}" }
    private fun identity() = EchoAudioResumeIdentity(player.currentMediaItem?.mediaId, player.currentMediaItemIndex, routeKey())
    private fun remember() = resume.remember(identity(), EchoPlaybackProcessRuntime.sleepTimerEndTimeEpochMs)
    private inline fun own(block: () -> Unit) { acting = true; try { block() } finally { acting = false } }
    private fun gain(value: Float) { EchoPlaybackProcessRuntime.setAudioFocusGain(value) }

    private fun requestFocus() {
        if (closed || held) return
        when (manager.requestAudioFocus(request)) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> { held = true; ducked = false; gain(1f) }
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> { remember(); own { player.pause() } }
            else -> { resume.cancel(); own { player.pause() }; gain(1f) }
        }
    }

    private fun pauseForFocus() {
        if (player.playWhenReady) remember()
        ducked = false
        gain(1f)
        own { player.pause() }
    }

    private fun onFocus(change: Int) {
        if (closed) return
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                held = true; ducked = false; gain(1f)
                val canPlay = !player.playWhenReady && player.mediaItemCount > 0 &&
                    player.playbackState != Player.STATE_ENDED && player.playerError == null && canInteract()
                if (resume.consume(identity(), canPlay, System.currentTimeMillis())) own { player.play() }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { held = false; pauseForFocus() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                held = false
                if (EchoPlaybackRuntimeOptionsStore.options.value.audioInterruptionPolicy == EchoAudioInterruptionPolicy.Pause ||
                    EchoPlaybackProcessRuntime.usbBitPerfectEnabled) pauseForFocus()
                else if (player.playWhenReady) { ducked = true; gain(0.2f) }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                held = false; ducked = false; resume.cancel(); gain(1f)
                own { player.pause() }
                manager.abandonAudioFocusRequest(request)
            }
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (acting || closed) return
        if (playWhenReady) requestFocus() else {
            resume.cancel(); held = false; ducked = false; gain(1f)
            manager.abandonAudioFocusRequest(request)
        }
    }
    override fun onPlayerError(error: PlaybackException) { resume.cancel() }
    override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) resume.cancel()
    }
    override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
        resume.cancel()
    }
    override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) resume.cancel()
    }
    override fun close() {
        closed = true; resume.cancel(); optionsJob?.cancel(); routeJob?.cancel()
        player.removeListener(this); manager.abandonAudioFocusRequest(request); gain(1f)
    }

    companion object {
        private val UserIntentCommands = setOf(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_STOP,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_CHANGE_MEDIA_ITEMS, Player.COMMAND_SET_MEDIA_ITEM)
    }
}
