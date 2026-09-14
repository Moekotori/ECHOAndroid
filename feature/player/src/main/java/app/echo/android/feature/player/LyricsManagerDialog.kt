package app.echo.android.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.lyrics.EchoLyricsCandidate

@Composable
fun LyricsManagerDialog(
    trackTitle: String,
    candidates: List<EchoLyricsCandidate>,
    searching: Boolean,
    selectedId: String? = null,
    error: String?,
    onSearch: () -> Unit,
    onChoose: (String) -> Unit,
    onImport: () -> Unit,
    onRemove: () -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var previewId by remember(trackTitle) { mutableStateOf<String?>(null) }
    var searched by remember(trackTitle) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lyrics_manager_title, trackTitle)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(stringResource(R.string.lyrics_manager_hint))
                    TextButton(onClick = { searched = true; onSearch() }, enabled = !searching) {
                        Text(stringResource(R.string.lyrics_manager_search))
                    }
                    TextButton(onClick = onImport) { Text(stringResource(R.string.lyrics_manager_import)) }
                    TextButton(onClick = onRemove) { Text(stringResource(R.string.lyrics_manager_clear)) }
                    Row {
                        TextButton(onClick = { onAdjustOffset(-50L) }) { Text("−50 ms") }
                        TextButton(onClick = { onAdjustOffset(50L) }) { Text("+50 ms") }
                    }
                    Text(stringResource(R.string.lyrics_manager_align_hint),
                        style = MaterialTheme.typography.bodySmall)
                    if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (searched && !searching && candidates.isEmpty() && error == null) {
                        Text(stringResource(R.string.lyrics_manager_empty))
                    }
                }
                items(candidates, key = { it.id }) { candidate ->
                    Column(Modifier.fillMaxWidth().clickable { previewId = candidate.id }.padding(vertical = 8.dp)) {
                        Text("${candidate.title} · ${candidate.artist}", style = MaterialTheme.typography.titleSmall)
                        Text(listOfNotNull(candidate.lyrics.sourceLabel, candidate.album,
                            candidate.durationMs.takeIf { it > 0 }?.let { "${it / 1000}s" },
                            if (candidate.lyrics.lines.any { it.words.isNotEmpty() }) {
                                stringResource(R.string.lyrics_manager_word_timed)
                            } else null
                        ).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        if (previewId == candidate.id) {
                            candidate.lyrics.lines.filter { it.text.isNotBlank() }.take(6).forEach { line ->
                                Text(line.text, Modifier.padding(top = 6.dp))
                                line.translation?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                line.romanization?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                            TextButton(onClick = { onChoose(candidate.id) }) {
                                Text(stringResource(
                                    if (candidate.id == selectedId) R.string.lyrics_manager_saved
                                    else R.string.lyrics_manager_use,
                                ))
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lyrics_manager_done)) } },
    )
}
