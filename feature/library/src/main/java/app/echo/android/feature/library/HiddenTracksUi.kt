package app.echo.android.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.paging.PagingData
import app.echo.android.design.EchoIcon
import app.echo.android.design.displayTrack
import app.echo.android.model.library.EchoHiddenTrack
import app.echo.android.model.library.EchoTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class LibraryHiddenTrackActions(val count: Flow<Int>, val pages: () -> Flow<PagingData<EchoHiddenTrack>>,
    val hide: suspend (List<String>) -> Unit, val restore: suspend (List<String>) -> Int,
    val restoreAll: suspend () -> Int)

internal class HiddenTracksUiState(val count: Int, val requestHide: (EchoTrack) -> Unit, val open: () -> Unit)
internal val LocalHiddenTracksUi = staticCompositionLocalOf<HiddenTracksUiState?> { null }

@Composable
fun LibraryHiddenTracksProvider(actions: LibraryHiddenTrackActions, content: @Composable () -> Unit) {
    val count by actions.count.collectAsState(0)
    var pending by remember { mutableStateOf<EchoTrack?>(null) }
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    CompositionLocalProvider(LocalHiddenTracksUi provides HiddenTracksUiState(count,
        { pending = it; failed = false }, { open = true })) { content() }
    pending?.let { track -> AlertDialog(
        onDismissRequest = { if (!busy) pending = null },
        title = { Text(stringResource(R.string.hidden_track_title)) },
        text = { Column {
            Text(stringResource(R.string.hidden_track_confirm, displayTrack(track.title)))
            if (failed) Text(stringResource(R.string.hidden_track_failed), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch { try { actions.hide(listOf(track.id)); pending = null }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true }
                finally { busy = false } }
        }) { Text(stringResource(R.string.hidden_track_hide)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) {
            Text(stringResource(R.string.hidden_track_cancel)) } },
    ) }
    if (open) HiddenTracksSheet(actions) { open = false }
}

@Composable
internal fun LibraryHiddenTracksAccess() {
    val state = LocalHiddenTracksUi.current ?: return
    if (state.count > 0) IconButton(onClick = state.open) {
        EchoIcon(Icons.Rounded.VisibilityOff, stringResource(R.string.hidden_track_count, state.count))
    }
}

