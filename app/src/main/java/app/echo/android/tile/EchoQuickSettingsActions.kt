package app.echo.android.tile

import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.content.ComponentName
import app.echo.android.data.EchoSettingsStore
import app.echo.android.playback.EchoPlaybackProcessRuntime
import app.echo.android.playback.EchoPlaybackService
import app.echo.android.playback.EchoPlaybackSessionCommands
import app.echo.android.playback.EchoSleepTimerTilePolicy
import app.echo.android.playback.EchoSleepTimerTileStep
import app.echo.android.widget.EchoPlaybackRemote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@UnstableApi
object EchoQuickSettingsActions {
    fun cycleSleepTimer() {
        when (
            EchoSleepTimerTilePolicy.next(
                EchoPlaybackProcessRuntime.sleepTimerMode,
                EchoPlaybackProcessRuntime.sleepTimerRequestedMinutes,
            )
        ) {
            EchoSleepTimerTileStep.Minutes15 -> EchoPlaybackProcessRuntime.setSleepTimerMinutes(15)
            EchoSleepTimerTileStep.Minutes30 -> EchoPlaybackProcessRuntime.setSleepTimerMinutes(30)
            EchoSleepTimerTileStep.Minutes60 -> EchoPlaybackProcessRuntime.setSleepTimerMinutes(60)
            EchoSleepTimerTileStep.EndOfTrack -> EchoPlaybackProcessRuntime.setSleepTimerEndOfTrack()
            EchoSleepTimerTileStep.Off -> EchoPlaybackProcessRuntime.cancelSleepTimer()
        }
    }

    fun toggleUsbExclusive(context: Context) {
        val enable = !EchoPlaybackProcessRuntime.usbExclusiveEnabled
        val app = context.applicationContext
        EchoPlaybackProcessRuntime.usbAudioMonitor(app).setExclusiveEnabled(enable)
        EchoPlaybackProcessRuntime.setUsbOutputMode(
            exclusive = enable,
            strict = enable && EchoPlaybackProcessRuntime.usbBitPerfectEnabled,
        )
        EchoPlaybackProcessRuntime.scope.launch(Dispatchers.IO) {
            EchoSettingsStore(app).setUsbExclusiveEnabled(enable)
        }
    }

    fun toggleFavorite(context: Context) {
        withController(context) { controller ->
            controller.sendCustomCommand(EchoPlaybackSessionCommands.toggleFavorite, Bundle.EMPTY)
        }
    }

    fun skipToNext(context: Context) {
        EchoPlaybackRemote.skipToNext(context)
    }

    fun sleepRemainingMs(): Long {
        val surface = EchoPlaybackProcessRuntime.surfaceSnapshot
        val duration = surface.durationMs
        return EchoPlaybackProcessRuntime.sleepTimerRemainingMs(
            trackRemainingMs = (duration - surface.positionMs).coerceAtLeast(0L),
            trackDurationKnown = duration > 0L,
        )
    }

    suspend fun isCurrentFavorite(): Boolean {
        val mediaId = EchoPlaybackProcessRuntime.surfaceSnapshot.mediaId ?: return false
        return EchoPlaybackProcessRuntime.catalog().isFavorite(mediaId)
    }

    private fun withController(context: Context, block: (MediaController) -> Unit) {
        val app = context.applicationContext
        val future = MediaController.Builder(
            app,
            SessionToken(app, ComponentName(app, EchoPlaybackService::class.java)),
        ).buildAsync()
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
                try {
                    block(controller)
                } finally {
                    controller.release()
                }
            },
            ContextCompat.getMainExecutor(app),
        )
    }
}
