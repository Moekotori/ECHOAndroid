package app.echo.android.playback

import android.content.Context
import android.os.Process
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@UnstableApi
class EchoPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(echoPlaybackDataSourceFactory(this)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { it.addListener(EchoPlayerListener()) }

        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setId("echo-mobile-main-session")
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession.takeIf {
            EchoMediaSessionControllerGate.isAllowed(
                context = this,
                controllerInfo = controllerInfo,
            )
        }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    private class EchoPlayerListener : Player.Listener {
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            super.onPlayerError(error)
        }

        override fun onEvents(player: Player, events: Player.Events) {
            super.onEvents(player, events)
        }
    }
}

internal object EchoMediaSessionControllerGate {
    @UnstableApi
    fun isAllowed(context: Context, controllerInfo: MediaSession.ControllerInfo): Boolean {
        if (controllerInfo.isTrusted) return true
        if (controllerInfo.uid == Process.myUid() || controllerInfo.uid == Process.SYSTEM_UID) return true
        return controllerInfo.packageName == context.applicationContext.packageName
    }
}
