package app.echo.android

import android.app.Application
import android.content.ComponentName
import android.service.quicksettings.TileService
import androidx.glance.appwidget.updateAll
import androidx.media3.common.util.UnstableApi
import app.echo.android.playback.EchoPlaybackProcessRuntime
import app.echo.android.tile.EchoPlaybackTileService
import app.echo.android.widget.EchoPlaybackWidget
import app.echo.android.widget.EchoPlaybackWidgetLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
object EchoPlaybackSurfaces {
    fun bind(application: Application) {
        EchoPlaybackProcessRuntime.scope.launch {
            combine(
                EchoPlaybackProcessRuntime.surface,
                EchoPlaybackProcessRuntime.notificationLyricLine,
            ) { snapshot, lyric -> EchoPlaybackWidgetLayout.chrome(snapshot, lyric) }
                .distinctUntilChanged()
                .collect {
                    withContext(Dispatchers.Default) {
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
}
