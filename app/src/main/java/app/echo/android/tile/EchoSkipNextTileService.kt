package app.echo.android.tile

import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.media3.common.util.UnstableApi
import app.echo.android.R
import app.echo.android.i18n.wrapEchoAppLocaleToMatchApplication
import app.echo.android.playback.EchoPlaybackProcessRuntime

@UnstableApi
class EchoSkipNextTileService : TileService() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.wrapEchoAppLocaleToMatchApplication())
    }

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        if (!EchoPlaybackProcessRuntime.surfaceSnapshot.hasTrack) return
        EchoQuickSettingsActions.skipToNext(this)
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val snapshot = EchoPlaybackProcessRuntime.surfaceSnapshot
        tile.state = if (snapshot.hasTrack) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.qs_tile_next)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = snapshot.title.ifBlank { getString(R.string.qs_tile_idle) }
        }
        tile.updateTile()
    }
}
