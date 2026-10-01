@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package app.echo.android.widget

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import app.echo.android.playback.EchoPlaybackService
import app.echo.android.playback.PlaybackSessionPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal enum class EchoWidgetPlaybackCommand { Play, Toggle, Next, Previous }
private val commandMutex = Mutex()

/** One controller at a time; acquisition, resumption, and waiting all share an 8-second bound. */
internal suspend fun executeWidgetPlaybackCommand(context: Context, command: EchoWidgetPlaybackCommand): Boolean = try {
    withTimeoutOrNull(8_000) {
        commandMutex.withLock {
            withContext(Dispatchers.Main.immediate) {
                val controller = connectWidgetController(context.applicationContext)
                try {
                    if (controller.mediaItemCount == 0 && !readWidgetPlaybackSnapshot(context).hasTrack) return@withContext false
                    when (command) {
                        EchoWidgetPlaybackCommand.Play, EchoWidgetPlaybackCommand.Toggle -> {
                            if (command == EchoWidgetPlaybackCommand.Toggle && controller.playWhenReady) controller.pause()
                            else {
                                if (PlaybackSessionPolicy.shouldPrepareBeforePlay(controller.playerError != null,
                                        controller.playbackState == Player.STATE_IDLE)) controller.prepare()
                                controller.play()
                                // A queue can arrive from the service's passive restore before play resumption completes.
                                // Retain this controller until the actual playback intent has been applied.
                                if (!awaitWidgetPlayer(controller, context, waitForPlayback = true)) return@withContext false
                            }
                        }
                        EchoWidgetPlaybackCommand.Next, EchoWidgetPlaybackCommand.Previous -> {
                            if (!awaitWidgetPlayer(controller, context, waitForPlayback = false)) return@withContext false
                            if (command == EchoWidgetPlaybackCommand.Next) {
                                if (!controller.hasNextMediaItem()) return@withContext false
                                controller.seekToNextMediaItem()
                            } else controller.seekToPrevious()
                            if (PlaybackSessionPolicy.shouldPrepareAfterExternalSkip(controller.playerError != null,
                                    controller.playbackState == Player.STATE_IDLE, controller.mediaItemCount)) controller.prepare()
                        }
                    }
                    true
                } finally { controller.release() }
            }
        }
    } ?: false
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    EchoErrorLog.record(EchoErrorSource.Playback, "Playback control could not connect to the media session.", throwable = error)
    false
}

private suspend fun connectWidgetController(context: Context): MediaController = suspendCancellableCoroutine { continuation ->
    val executor = ContextCompat.getMainExecutor(context)
    val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, EchoPlaybackService::class.java)))
        .buildAsync()
    continuation.invokeOnCancellation { executor.execute { MediaController.releaseFuture(future) } }
    future.addListener({
        try {
            continuation.resume(future.get()) { _, controller, _ -> executor.execute { controller.release() } }
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }, executor)
}

private suspend fun awaitWidgetPlayer(controller: MediaController, context: Context, waitForPlayback: Boolean): Boolean {
    fun ready(player: Player): Boolean = player.mediaItemCount > 0 && (!waitForPlayback || player.isPlaying)
    if (ready(controller)) return true
    if (controller.playerError != null) return false
    return suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(context)
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (ready(player) || player.playerError != null) {
                    player.removeListener(this)
                    if (continuation.isActive) continuation.resume(ready(player) && player.playerError == null)
                }
            }
        }
        controller.addListener(listener)
        continuation.invokeOnCancellation { executor.execute { controller.removeListener(listener) } }
    }
}
