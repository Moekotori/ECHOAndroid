package app.echo.android.tile

import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.media3.common.util.UnstableApi
import app.echo.android.EchoPlaybackSurfaces
import app.echo.android.R
import app.echo.android.i18n.wrapEchoAppLocaleToMatchApplication
import app.echo.android.playback.EchoPlaybackProcessRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class EchoFavoriteTileService : TileService() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.wrapEchoAppLocaleToMatchApplication())
    }

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        if (!EchoPlaybackProcessRuntime.surfaceSnapshot.hasTrack) return
        EchoQuickSettingsActions.toggleFavorite(this)
        EchoPlaybackProcessRuntime.scope.launch {
            kotlinx.coroutines.delay(250)
            refresh()
        }
        EchoPlaybackSurfaces.requestTileRefresh(applicationContext)
    }

    private fun refresh() {
        val snapshot = EchoPlaybackProcessRuntime.surfaceSnapshot
        val tile = qsTile ?: return
        if (!snapshot.hasTrack) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.qs_tile_favorite)
            if (Build.VERSION.SDK_INT >= 29) tile.subtitle = getString(R.string.qs_tile_idle)
            tile.updateTile()
            return
        }
        EchoPlaybackProcessRuntime.scope.launch {
            val liked = withContext(Dispatchers.IO) { EchoQuickSettingsActions.isCurrentFavorite() }
            tile.state = if (liked) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = getString(R.string.qs_tile_favorite)
            if (Build.VERSION.SDK_INT >= 29) {
                tile.subtitle = snapshot.title.ifBlank { getString(R.string.qs_tile_favorite) }
            }
            tile.updateTile()
        }
    }
}
