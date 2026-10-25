package app.echo.android.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalHiddenTrackActions = staticCompositionLocalOf { emptySet<app.echo.android.model.settings.EchoTrackMenuAction>() }
internal val LocalLibraryFileNames = staticCompositionLocalOf { false }
internal val LocalLibraryPlayNext = staticCompositionLocalOf { false }

/** Library-wide presentation wiring; feature-specific state remains owned by its provider. */
@Composable
fun LibraryPresentationProvider(actions: LibraryHiddenTrackActions, showFileNames: Boolean,
    hiddenTrackActions: Set<app.echo.android.model.settings.EchoTrackMenuAction> = emptySet(),
    showPlayNext: Boolean = false,
    content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLibraryFileNames provides showFileNames, LocalHiddenTrackActions provides hiddenTrackActions,
        LocalLibraryPlayNext provides showPlayNext) {
        LibraryHiddenTracksProvider(actions, content = content)
    }
}
