package app.echo.android.lock

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import app.echo.android.feature.player.LockLyricsScene
import app.echo.android.playback.EchoPlaybackProcessRuntime
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@UnstableApi
@Composable
internal fun EchoAutomaticLockLyrics(
    activity: ComponentActivity?,
    session: EchoLockLyricsSession,
    screen: EchoLockLyricsScreen,
    enabled: Boolean,
    isPlaying: Boolean,
    title: String,
    artist: String,
    artworkUri: String?,
    wordHighlightEnabled: Boolean,
    estimatedWordHighlightEnabled: Boolean,
) {
    if (activity == null) return
    val lyrics = EchoPlaybackProcessRuntime.lyricDisplaySnapshot
    val hasCurrentLine by remember(lyrics) {
        lyrics.map { it.current != null }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(lyrics.value.current != null)
    fun shouldShowWindow(dismissed: Boolean) = EchoLockLyricsPolicy.shouldKeepShowWhenLocked(
        enabled, isPlaying, hasCurrentLine, dismissed,
    )
    DisposableEffect(activity, session, enabled, isPlaying, hasCurrentLine) {
        // Releasing the lock screen stops the Activity and may suspend Compose frames.
        val windowUpdates = activity.lifecycleScope.launch {
            session.screen.map { shouldShowWindow(it.dismissed) }.distinctUntilChanged()
                .collect { activity.setLockLyricsWindow(it) }
        }
        onDispose {
            windowUpdates.cancel()
            activity.setLockLyricsWindow(false)
        }
    }
    if (shouldShowWindow(screen.dismissed) && screen.interactive && screen.keyguardLocked) {
        val snapshot by lyrics.collectAsStateWithLifecycle()
        val dismiss = {
            session.dismiss()
            activity.setLockLyricsWindow(false)
        }
        BackHandler(onBack = dismiss)
        LockLyricsScene(
            title = title,
            artist = artist,
            artworkUri = artworkUri,
            snapshot = snapshot,
            wordHighlightEnabled = wordHighlightEnabled,
            estimatedWordHighlightEnabled = estimatedWordHighlightEnabled,
            onDismiss = dismiss,
        )
    }
}

@Suppress("DEPRECATION")
private fun ComponentActivity.setLockLyricsWindow(show: Boolean) {
    if (Build.VERSION.SDK_INT >= 27) {
        setShowWhenLocked(show)
    } else if (show) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
    }
}
