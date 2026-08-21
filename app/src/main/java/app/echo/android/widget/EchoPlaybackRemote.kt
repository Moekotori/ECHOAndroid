package app.echo.android.widget

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.echo.android.playback.EchoPlaybackService
import app.echo.android.playback.PlaybackSessionPolicy

@UnstableApi
object EchoPlaybackRemote {
    fun play(context: Context) {
        withController(context) { controller ->
            if (controller.playbackState == Player.STATE_IDLE) {
                controller.prepare()
            }
            controller.play()
        }
    }

    fun togglePlayPause(context: Context) {
        withController(context) { controller ->
            if (controller.playWhenReady) {
                controller.pause()
            } else {
                play(controller)
            }
        }
    }

    private fun play(controller: MediaController) {
        if (
            PlaybackSessionPolicy.shouldPrepareBeforePlay(
                hasPlayerError = controller.playerError != null,
                playbackStateIdle = controller.playbackState == Player.STATE_IDLE,
            )
        ) {
            controller.prepare()
        }
        controller.play()
    }

    fun skipToNext(context: Context) {
        withController(context) { controller ->
            if (controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem()
                if (
                    PlaybackSessionPolicy.shouldPrepareAfterExternalSkip(
                        hasPlayerError = controller.playerError != null,
                        playbackStateIdle = controller.playbackState == Player.STATE_IDLE,
                        mediaItemCount = controller.mediaItemCount,
                    )
                ) {
                    controller.prepare()
                }
            }
        }
    }

    fun skipToPrevious(context: Context) {
        withController(context) { controller ->
            controller.seekToPrevious()
            if (
                PlaybackSessionPolicy.shouldPrepareAfterExternalSkip(
                    hasPlayerError = controller.playerError != null,
                    playbackStateIdle = controller.playbackState == Player.STATE_IDLE,
                    mediaItemCount = controller.mediaItemCount,
                )
            ) {
                controller.prepare()
            }
        }
    }

    private fun withController(context: Context, block: (MediaController) -> Unit) {
        val appContext = context.applicationContext
        val token = SessionToken(
            appContext,
            ComponentName(appContext, EchoPlaybackService::class.java),
        )
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
                try {
                    block(controller)
                } finally {
                    controller.release()
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }
}
