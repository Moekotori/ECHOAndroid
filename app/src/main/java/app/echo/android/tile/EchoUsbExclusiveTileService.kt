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

@UnstableApi
class EchoUsbExclusiveTileService : TileService() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.wrapEchoAppLocaleToMatchApplication())
    }

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        EchoQuickSettingsActions.toggleUsbExclusive(this)
        refresh()
        EchoPlaybackSurfaces.requestTileRefresh(applicationContext)
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val exclusive = EchoPlaybackProcessRuntime.usbExclusiveEnabled
        tile.state = if (exclusive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.qs_tile_usb)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = getString(
                if (exclusive) R.string.qs_tile_usb_on else R.string.qs_tile_usb_off,
            )
        }
        tile.updateTile()
    }
}
