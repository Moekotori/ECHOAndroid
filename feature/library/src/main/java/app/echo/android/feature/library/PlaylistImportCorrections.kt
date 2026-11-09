package app.echo.android.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun PlaylistImportCorrections(preview: PlaylistImportPreview,
    search: suspend (String) -> List<PlaylistImportCandidate>,
    correct: (Int, PlaylistImportCandidate?) -> Unit, onDismiss: () -> Unit) {
    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var query by rememberSaveable(selectedIndex) { mutableStateOf("") }
    val reviewListState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val entry = selectedIndex?.let { preview.entries.getOrNull(it) }
    var matches by remember(selectedIndex, query) { mutableStateOf(emptyList<PlaylistImportCandidate>()) }
    var loading by remember(selectedIndex, query) { mutableStateOf(query.isNotBlank()) }
    var failed by remember(selectedIndex, query) { mutableStateOf(false) }
    LaunchedEffect(selectedIndex, query) {
        if (entry == null || query.isBlank()) return@LaunchedEffect
        try { delay(200); matches = search(query.trim()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true; matches = emptyList() }
        finally { loading = false }
    }
    val close = {
        if (entry != null) {
            keyboard?.hide()
            selectedIndex = null
        } else onDismiss()
    }
    AlertDialog(onDismissRequest = close,
        title = { Text(stringResource(if (entry == null) R.string.playlist_import_review else R.string.playlist_import_choose_match)) },
        text = { Column(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
            if (entry != null) {
                Text(entry.name, style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(query, { query = it.take(200) },
                    label = { Text(stringResource(R.string.playlist_import_search_match)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }))
                val candidates = if (query.isBlank()) entry.candidates else matches
                when {
                    loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    failed -> Text(stringResource(R.string.playlist_action_error), color = MaterialTheme.colorScheme.error)
                    candidates.isEmpty() -> Text(
                        stringResource(if (query.isBlank()) R.string.playlist_import_no_suggestions else R.string.library_no_matches),
                        modifier = Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(candidates, key = { it.id }) { candidate ->
                        Column(Modifier.fillMaxWidth().clickable { correct(entry.index, candidate); close() }.padding(vertical = 12.dp)) {
                            Text(candidate.title, style = MaterialTheme.typography.bodyLarge)
                            Text(candidate.artist, style = MaterialTheme.typography.bodySmall)
                            Text(candidate.location, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                TextButton(onClick = { correct(entry.index, null); close() }) { Text(stringResource(R.string.playlist_import_skip_entry)) }
            } else LazyColumn(state = reviewListState) {
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
        confirmButton = { TextButton(onClick = close) {
            Text(stringResource(if (entry != null) R.string.feature_library_back_49093c else R.string.playlist_import_review_done))
        } })
}
