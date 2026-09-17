package app.echo.android.lock

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import android.os.PowerManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.media3.common.util.UnstableApi
import app.echo.android.data.EchoSettingsStore
import app.echo.android.design.EchoMobileTheme
import app.echo.android.feature.player.LockLyricsScene
import app.echo.android.model.settings.EchoColorTheme
import app.echo.android.model.settings.EchoPerformanceMode
import app.echo.android.playback.EchoPlaybackProcessRuntime

@UnstableApi
@Composable
fun EchoLockLyricsHost() {
    val context = LocalContext.current
    val settingsStore = remember(context) { EchoSettingsStore(context.applicationContext) }
    val settings by settingsStore.appSettings.collectAsState(
        initial = settingsStore.startupAppSettingsSnapshot(),
    )
    val snapshot by EchoPlaybackProcessRuntime.lyricDisplaySnapshot.collectAsState()
    val surface by EchoPlaybackProcessRuntime.surface.collectAsState()
    val powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    val performance = remember(settings.performanceMode, powerSave) {
        EchoPerformanceMode.fromId(settings.performanceMode).resolve(powerSave)
    }
    EchoMobileTheme(
        darkTheme = true,
        dynamicColor = settings.dynamicColorEnabled,
        colorTheme = EchoColorTheme.fromId(settings.colorTheme),
        playbackHapticsEnabled = false,
        effectivePerformanceMode = performance,
    ) {
        LockLyricsScene(
            title = surface.title,
            artist = surface.artist,
            artworkUri = surface.artworkUri,
            snapshot = snapshot,
            wordHighlightEnabled = settings.lyricsWordHighlightEnabled,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
