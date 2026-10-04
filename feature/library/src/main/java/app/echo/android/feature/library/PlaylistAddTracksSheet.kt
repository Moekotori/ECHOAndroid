package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import app.echo.android.design.ArtworkTile
import app.echo.android.design.displayMetadataOrUnknown
import app.echo.android.model.library.PlaylistImportLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistAddTracksSheet(id: String, actions: PlaylistManagementActions, onAdded: (Int) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var settledQuery by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(query) { delay(220); settledQuery = query }
    val tracks = remember(actions, settledQuery) { actions.candidates(settledQuery) }.collectAsLazyPagingItems()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !busy })
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.playlist_add_tracks), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = query, onValueChange = { query = it }, enabled = !busy, singleLine = true,
                label = { Text(stringResource(R.string.feature_library_search_songs_artists_albums_14dc2c)) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
            if (failed) Text(stringResource(R.string.playlist_action_error), color = MaterialTheme.colorScheme.error)
            if (selected.size >= PlaylistImportLimits.MaxEntries) Text(stringResource(R.string.playlist_selection_limit),
                style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f)) {
                if (tracks.isInitialPagingLoad()) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
                else if (tracks.isInitialPagingError()) item { Text(stringResource(R.string.feature_library_failed_to_load_playlist_tracks_9130da)) }
                else if (tracks.itemCount == 0) item { Text(stringResource(R.string.playlist_add_no_matches)) }
                items(tracks.itemCount, key = { tracks.peek(it)?.id ?: "add-$it" }, contentType = { "candidate" }) { index ->
                    tracks[index]?.let { track ->
                        val checked = track.id in selected
                        Row(Modifier.fillMaxWidth().toggleable(checked, enabled = !busy &&
                            (checked || selected.size < PlaylistImportLimits.MaxEntries), role = Role.Checkbox,
                            onValueChange = { selected = if (it) selected + track.id else selected - track.id })
                            .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Checkbox(checked, onCheckedChange = null)
                            ArtworkTile(track.artworkUri, Modifier.size(36.dp), accent = MaterialTheme.colorScheme.onSurfaceVariant,
                                cornerRadius = 7.dp, elevation = 0.dp)
                            Column(Modifier.weight(1f)) {
                                Text(displayMetadataOrUnknown(track.title, unknownTrackLabel()), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(displayMetadataOrUnknown(track.artist, unknownArtistLabel()), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(pluralStringResource(R.plurals.playlist_selected, selected.size, selected.size), Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    busy = true; failed = false
                    scope.launch {
                        try { val count = actions.add(id, selected.toList()); onAdded(count); onDismiss() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failed = true }
                        finally { busy = false }
                    }
                }, enabled = selected.isNotEmpty() && !busy) {
                    Text(stringResource(if (busy) R.string.playlist_import_saving else R.string.feature_library_add_to_playlist_0f8338))
                }
            }
        }
    }
}
