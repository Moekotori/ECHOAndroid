package app.echo.android.tile

import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.media3.common.util.UnstableApi
import app.echo.android.EchoPlaybackSurfaces
import app.echo.android.R
import app.echo.android.i18n.wrapEchoAppLocaleToMatchApplication
import app.echo.android.model.playback.EchoSleepTimerMode
import app.echo.android.playback.EchoPlaybackProcessRuntime

@UnstableApi
class EchoSleepTimerTileService : TileService() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.wrapEchoAppLocaleToMatchApplication())
    }

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        EchoQuickSettingsActions.cycleSleepTimer()
        refresh()
        EchoPlaybackSurfaces.requestTileRefresh(applicationContext)
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val mode = EchoPlaybackProcessRuntime.sleepTimerMode
        tile.state = if (mode == EchoSleepTimerMode.Off) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.label = getString(R.string.qs_tile_sleep)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when (mode) {
                EchoSleepTimerMode.Off -> getString(R.string.qs_tile_sleep_off)
                EchoSleepTimerMode.EndOfTrack -> getString(R.string.qs_tile_sleep_end)
                EchoSleepTimerMode.Timed -> {
                    val minutes = (EchoQuickSettingsActions.sleepRemainingMs() / 60_000L).coerceAtLeast(0L)
                    getString(R.string.qs_tile_sleep_minutes, minutes.toInt())
                }
            }
        }
        tile.updateTile()
    }
}
