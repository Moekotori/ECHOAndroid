package app.echo.android.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoModalSheet
import app.echo.android.model.library.EchoPlaylist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPlaylistSheet(playlists: List<EchoPlaylist>, busy: Boolean, failed: Boolean,
    onSelect: (EchoPlaylist) -> Unit, onDismiss: () -> Unit) {
    val editable = remember(playlists) { playlists.filter { it.canEdit } }
    EchoModalSheet(onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), sheetGesturesEnabled = !busy) {
        LazyColumn(contentPadding = PaddingValues(24.dp)) {
            item { Text(stringResource(R.string.search_add_to_playlist), style = MaterialTheme.typography.titleLarge) }
            if (failed) item { Text(stringResource(R.string.search_action_failed), color = MaterialTheme.colorScheme.error) }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (editable.isEmpty()) item { Text(stringResource(R.string.search_no_playlists)) }
            items(editable, key = { it.id }) { playlist ->
                TextButton(onClick = { onSelect(playlist) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(playlist.name) }
            }
        }
    }
}
