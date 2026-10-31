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
    onNext: () -> Unit = {}, onPrevious: () -> Unit = {},
    onSeekBy: (Long) -> Unit = {}, onSearch: () -> Unit = {},
) {
    val activity = LocalActivity.current as? MainActivity
    val enabled = rememberUpdatedState(navigationEnabled)
    val tabs = rememberUpdatedState(onSelectTab)
    val settings = rememberUpdatedState(onSettings)
    val playback = rememberUpdatedState(onPlayPause)
    val next = rememberUpdatedState(onNext); val previous = rememberUpdatedState(onPrevious)
    val seek = rememberUpdatedState(onSeekBy); val search = rememberUpdatedState(onSearch)
    var help by remember { mutableStateOf(false) }
    if (help) app.echo.android.feature.settings.KeyboardShortcutsDialog { help = false }
    DisposableEffect(activity) {
        val handler: (Int) -> Boolean = { command ->
            when {
                !enabled.value || help -> false
                command == 5 -> { playback.value(); true }
                command == 6 -> { next.value(); true }
                command == 7 -> { previous.value(); true }
                command == 8 -> { seek.value(-10_000); true }
                command == 9 -> { seek.value(10_000); true }
                command == 10 -> { search.value(); true }
                command == 11 -> { help = true; true }
                command == 4 -> { settings.value(); true }
                command in EchoTab.entries.indices -> { tabs.value(EchoTab.entries[command]); true }
                else -> false
            }
        }
        activity?.desktopShortcutHandler = handler
        onDispose { if (activity?.desktopShortcutHandler === handler) activity.desktopShortcutHandler = null }
    }
}
