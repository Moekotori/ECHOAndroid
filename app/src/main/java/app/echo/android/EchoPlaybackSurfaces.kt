package app.echo.android

import android.app.Application
import android.content.ComponentName
import android.service.quicksettings.TileService
import androidx.glance.appwidget.updateAll
import androidx.media3.common.util.UnstableApi
import app.echo.android.playback.EchoPlaybackProcessRuntime
import app.echo.android.tile.EchoPlaybackTileService
import app.echo.android.widget.EchoPlaybackWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@UnstableApi
object EchoPlaybackSurfaces {
    fun bind(application: Application) {
        EchoPlaybackProcessRuntime.setSurfaceListener {
            EchoPlaybackProcessRuntime.scope.launch(Dispatchers.Default) {
                runCatching { EchoPlaybackWidget().updateAll(application) }
                runCatching {
                    TileService.requestListeningState(
                        application,
                        ComponentName(application, EchoPlaybackTileService::class.java),
                    )
                }
            }
        }
    }
}
