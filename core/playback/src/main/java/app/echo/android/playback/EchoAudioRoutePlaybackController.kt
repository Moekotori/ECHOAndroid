package app.echo.android.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.EchoAudioRoutePlaybackPolicy
import app.echo.android.model.playback.EchoOutputDeviceKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@UnstableApi
class EchoAudioRoutePlaybackController(
    context: Context,
    private val player: Player,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private var started = false
    private var previousKind = EchoOutputDeviceKind.System
    private var pausedByDisconnect = false
    private var acting = false
    private var routeJob: Job? = null
    private var resumeJob: Job? = null

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            val options = EchoPlaybackProcessRuntime.audioRoutePlaybackOptions()
            if (EchoAudioRoutePlaybackPolicy.shouldPauseForBecomingNoisy(options, player.playWhenReady)) {
                pauseForDisconnect()
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (acting) return
            pausedByDisconnect = false
            resumeJob?.cancel()
        }
    }

    fun start() {
        if (started) return
        started = true
        previousKind = EchoOutputDeviceKind.fromId(
            EchoPlaybackProcessRuntime.outputRouteMonitor(appContext).route.value.kind,
        )
        player.addListener(listener)
        ContextCompat.registerReceiver(
            appContext,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        routeJob = scope.launch {
            EchoPlaybackProcessRuntime.outputRouteMonitor(appContext).route.collect { route ->
                onRouteKind(EchoOutputDeviceKind.fromId(route.kind))
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        resumeJob?.cancel()
        resumeJob = null
        routeJob?.cancel()
        routeJob = null
        player.removeListener(listener)
        runCatching { appContext.unregisterReceiver(noisyReceiver) }
        pausedByDisconnect = false
    }

    private fun onRouteKind(current: EchoOutputDeviceKind) {
        val previous = previousKind
        if (current == previous) return
        previousKind = current
        val options = EchoPlaybackProcessRuntime.audioRoutePlaybackOptions()
        if (EchoAudioRoutePlaybackPolicy.shouldPauseForRouteLoss(options, player.playWhenReady, previous, current)) {
            resumeJob?.cancel()
            pauseForDisconnect()
            return
        }
        if (EchoAudioRoutePlaybackPolicy.shouldResumeForRouteGain(
                options,
                pausedByDisconnect,
                previous,
                current,
                canResume(),
            )
        ) {
            scheduleResume()
            return
        }
        if (!EchoAudioRoutePlaybackPolicy.isExternalOutput(current)) {
            resumeJob?.cancel()
        }
    }

    private fun pauseForDisconnect() {
        acting = true
        pausedByDisconnect = true
        player.pause()
        acting = false
    }

    private fun scheduleResume() {
        resumeJob?.cancel()
        resumeJob = scope.launch {
            delay(RESUME_DEBOUNCE_MS)
            if (!pausedByDisconnect || !canResume()) return@launch
            acting = true
            pausedByDisconnect = false
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
            acting = false
        }
    }

    private fun canResume(): Boolean =
        player.mediaItemCount > 0 && player.playbackState != Player.STATE_ENDED
}

private const val RESUME_DEBOUNCE_MS = 400L
