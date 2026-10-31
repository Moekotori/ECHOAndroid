package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.*

@Composable
fun ScannedPlaylistsPanel(result: EchoPlaylistDiscoveryResult?, busy: Boolean, error: Boolean,
    imported: Set<String>, onDiscover: () -> Unit, onChooseFolder: () -> Unit,
    onImport: (EchoDiscoveredPlaylist) -> Unit) {
    var selected by remember { mutableStateOf<EchoDiscoveredPlaylist?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.scanned_playlists_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.scanned_playlists_note), style = MaterialTheme.typography.bodySmall)
        Row {
            TextButton(enabled = !busy, onClick = onDiscover) { Text(stringResource(R.string.scanned_playlists_discover)) }
            TextButton(enabled = !busy, onClick = onChooseFolder) { Text(stringResource(R.string.scanned_playlists_choose)) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error) Text(stringResource(R.string.scanned_playlists_error), color = MaterialTheme.colorScheme.error)
        result?.let { found ->
            if (found.truncated || found.unavailableDirectories > 0) Text(stringResource(R.string.scanned_playlists_partial))
            if (found.items.isEmpty()) Text(stringResource(R.string.scanned_playlists_empty))
            found.items.forEach { candidate -> key(candidate.uri) {
                Text(candidate.name, style = MaterialTheme.typography.titleSmall)
                candidate.preview?.let { preview ->
                    Text(stringResource(R.string.scanned_playlists_counts, preview.trackIds.size, preview.unmatchedEntries,
                        preview.ambiguousEntries, preview.duplicateEntries), style = MaterialTheme.typography.bodySmall)
                }
                if (candidate.error) Text(stringResource(R.string.scanned_playlists_read_error))
                else if (candidate.uri in imported) Text(stringResource(R.string.scanned_playlists_imported))
                else TextButton(enabled = !busy && !candidate.preview?.trackIds.isNullOrEmpty(), onClick = { selected = candidate }) {
                    Text(stringResource(if (candidate.alreadyImported) R.string.scanned_playlists_update else R.string.scanned_playlists_review))
                }
            } }
        }
    }
    selected?.let { candidate ->
        val preview = candidate.preview!!
        LaunchedEffect(imported) { if (candidate.uri in imported) selected = null }
        AlertDialog(onDismissRequest = { if (!busy) selected = null }, title = { Text(candidate.name) },
            text = { Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (candidate.alreadyImported) R.string.scanned_playlists_replace_warning else R.string.scanned_playlists_confirm))
                Text(stringResource(R.string.scanned_playlists_counts, preview.trackIds.size, preview.unmatchedEntries,
                    preview.ambiguousEntries, preview.duplicateEntries))
                preview.unresolvedNames.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (error) Text(stringResource(R.string.scanned_playlists_error), color = MaterialTheme.colorScheme.error)
            } }, confirmButton = { TextButton(enabled = !busy, onClick = { onImport(candidate) }) {
                Text(stringResource(if (candidate.alreadyImported) R.string.scanned_playlists_update else R.string.scanned_playlists_import))
            } }, dismissButton = { TextButton(enabled = !busy, onClick = { selected = null }) { Text(stringResource(R.string.media_tools_close)) } })
    }
}
