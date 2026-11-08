package app.echo.android.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun PlaylistImportCorrections(preview: PlaylistImportPreview,
    search: suspend (String) -> List<PlaylistImportCandidate>,
    correct: (Int, PlaylistImportCandidate?) -> Unit, onDismiss: () -> Unit) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var query by remember(selectedIndex) { mutableStateOf("") }
    val entry = selectedIndex?.let { preview.entries.getOrNull(it) }
    var matches by remember(selectedIndex, query) { mutableStateOf(entry?.candidates.orEmpty()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(selectedIndex, query) {
        if (entry == null || query.isBlank()) { matches = entry?.candidates.orEmpty(); return@LaunchedEffect }
        loading = true; failed = false
        try { delay(200); matches = search(query.take(200)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true; matches = emptyList() }
        finally { loading = false }
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(if (entry == null) R.string.playlist_import_review else R.string.playlist_import_choose_match)) },
        text = { Column(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
            if (entry != null) {
                Text(entry.name, style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.playlist_import_search_match)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Text(stringResource(R.string.playlist_action_error), color = MaterialTheme.colorScheme.error)
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(matches, key = { it.id }) { candidate ->
                        Column(Modifier.fillMaxWidth().clickable { correct(entry.index, candidate); selectedIndex = null }.padding(vertical = 12.dp)) {
                            Text(candidate.title, style = MaterialTheme.typography.bodyLarge)
                            Text(candidate.artist, style = MaterialTheme.typography.bodySmall)
                            Text(candidate.location, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                TextButton(onClick = { correct(entry.index, null); selectedIndex = null }) { Text(stringResource(R.string.playlist_import_skip_entry)) }
            } else LazyColumn {
                items(preview.entries, key = { it.index }) { row ->
                    Column(Modifier.fillMaxWidth().clickable { selectedIndex = row.index }.padding(vertical = 10.dp)) {
                        Text("${row.index + 1}. ${row.name}", style = MaterialTheme.typography.bodyMedium)
                        Text(row.selected?.let { "${it.artist} · ${it.title}" } ?: stringResource(when (row.match) {
                            PlaylistImportMatch.Skipped -> R.string.playlist_import_skipped_entry
                            PlaylistImportMatch.Ambiguous -> R.string.playlist_import_ambiguous_entry
                            else -> R.string.playlist_import_missing_entry
                        }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } },
        confirmButton = { TextButton(onClick = { if (entry != null) selectedIndex = null else onDismiss() }) { Text(stringResource(R.string.playlist_import_review_done)) } })
}
