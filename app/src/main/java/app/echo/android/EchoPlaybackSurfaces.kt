package app.echo.android

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import androidx.glance.appwidget.updateAll
import androidx.media3.common.util.UnstableApi
import app.echo.android.playback.EchoPlaybackProcessRuntime
import app.echo.android.tile.EchoFavoriteTileService
import app.echo.android.tile.EchoPlaybackTileService
import app.echo.android.tile.EchoSkipNextTileService
import app.echo.android.tile.EchoSleepTimerTileService
import app.echo.android.tile.EchoUsbExclusiveTileService
import app.echo.android.widget.EchoPlaybackWidget
import app.echo.android.widget.EchoPlaybackWidgetLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
object EchoPlaybackSurfaces {
    private var binding: Job? = null
    private val tileServices = listOf(
        EchoPlaybackTileService::class.java,
        EchoSleepTimerTileService::class.java,
        EchoUsbExclusiveTileService::class.java,
        EchoFavoriteTileService::class.java,
        EchoSkipNextTileService::class.java,
    )

    fun bind(application: Application) {
        if (binding?.isActive == true) return
        binding = EchoPlaybackProcessRuntime.scope.launch {
            coroutineScope {
                launch(Dispatchers.Default) {
                    combine(EchoPlaybackProcessRuntime.surface, EchoPlaybackProcessRuntime.notificationLyricLine) {
                        snapshot, lyric -> EchoPlaybackWidgetLayout.chrome(snapshot, lyric)
                    }.distinctUntilChanged().conflate().collect {
                        try { EchoPlaybackWidget().updateAll(application) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { /* A removed/unsupported host must not end the collector. */ }
                        // Burst updates share the latest state; no idle polling or position-tick refreshes.
                        delay(250)
                    }
                }
                launch(Dispatchers.Default) {
                    EchoPlaybackProcessRuntime.surface.map {
                        EchoTilePlaybackChrome(it.title, it.artist, it.mediaId, it.hasTrack, it.isPlaying)
                    }.distinctUntilChanged().collect { requestTileRefresh(application) }
                }
            }
        }
    }

    fun requestTileRefresh(context: Context) {
        tileServices.forEach { service ->
            runCatching {
                TileService.requestListeningState(context, ComponentName(context, service))
            }
        }
    }
}

private data class EchoTilePlaybackChrome(
    val title: String, val artist: String, val mediaId: String?, val hasTrack: Boolean, val isPlaying: Boolean,
)
