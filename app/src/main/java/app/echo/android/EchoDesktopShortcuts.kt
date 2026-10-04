package app.echo.android

import androidx.compose.runtime.*
import androidx.activity.compose.LocalActivity

/** Ctrl+1…4 selects the dock, Ctrl+, opens settings, Ctrl+Space controls playback. */
@Composable
internal fun EchoDesktopShortcuts(
    navigationEnabled: Boolean,
    onSelectTab: (EchoTab) -> Unit,
    onSettings: () -> Unit,
    onPlayPause: () -> Unit,
) {
    val activity = LocalActivity.current as? MainActivity
    val enabled = rememberUpdatedState(navigationEnabled)
    val tabs = rememberUpdatedState(onSelectTab)
    val settings = rememberUpdatedState(onSettings)
    val playback = rememberUpdatedState(onPlayPause)
    DisposableEffect(activity) {
        val handler: (Int) -> Boolean = { command ->
            when {
                command == 5 -> { playback.value(); true }
                !enabled.value -> false
                command == 4 -> { settings.value(); true }
                command in EchoTab.entries.indices -> { tabs.value(EchoTab.entries[command]); true }
                else -> false
            }
        }
        activity?.desktopShortcutHandler = handler
        onDispose { if (activity?.desktopShortcutHandler === handler) activity.desktopShortcutHandler = null }
    }
}
