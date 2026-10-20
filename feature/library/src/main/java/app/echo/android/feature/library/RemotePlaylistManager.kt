package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import app.echo.android.model.library.EchoPlaylist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A separate server editor keeps read-only providers and local playlist actions unambiguous. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemotePlaylistManager(actions: RemoteLibraryWritebackActions, onDismiss: () -> Unit) {
    val playlists by actions.playlists.collectAsState(emptyList())
    var selectedId by remember(actions.sourceId) { mutableStateOf<String?>(null) }
    val selected = playlists.firstOrNull { it.id == selectedId }
    var adding by remember(selectedId) { mutableStateOf(false) }
    var query by remember(selectedId) { mutableStateOf("") }
    var settledQuery by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var nameTarget by remember { mutableStateOf<EchoPlaylist?>(null) }
    var nameVisible by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<EchoPlaylist?>(null) }
    val scope = rememberCoroutineScope()
    fun perform(operation: suspend () -> Unit) {
        if (busy) return
        busy = true; failed = false
        scope.launch {
            try { operation() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    LaunchedEffect(query) { delay(220); settledQuery = query }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !busy })) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).padding(horizontal = 20.dp)) {
            Text(selected?.name ?: stringResource(R.string.remote_playlist_manage), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.remote_playlist_server_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            if (failed) Text(stringResource(R.string.remote_playlist_error), color = MaterialTheme.colorScheme.error)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (selected == null) {
                TextButton(enabled = !busy, onClick = { nameTarget = null; name = ""; nameVisible = true }) {
                    Text(stringResource(R.string.feature_library_new_playlist_22cdbd))
                }
                LazyColumn(Modifier.weight(1f)) {
                    if (playlists.isEmpty()) item { Text(stringResource(R.string.remote_playlist_empty)) }
                    items(playlists, key = EchoPlaylist::id) { playlist ->
                        TextButton(enabled = !busy, onClick = { selectedId = playlist.id }, modifier = Modifier.fillMaxWidth()) {
                            Text(playlist.name, Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = !busy, onClick = { selectedId = null }) { Text(stringResource(R.string.remote_playlist_back)) }
                    TextButton(enabled = !busy, onClick = { nameTarget = selected; name = selected.name; nameVisible = true }) {
                        Text(stringResource(R.string.remote_playlist_rename))
                    }
                    TextButton(enabled = !busy, onClick = { deleteTarget = selected }) {
                        Text(stringResource(R.string.remote_playlist_delete))
                    }
                }
                TextButton(enabled = !busy, onClick = { adding = !adding; query = "" }) {
                    Text(stringResource(if (adding) R.string.remote_playlist_show_tracks else R.string.playlist_add_tracks))
                }
                if (adding) OutlinedTextField(query, { query = it }, enabled = !busy, singleLine = true,
                    label = { Text(stringResource(R.string.feature_library_search_songs_artists_albums_14dc2c)) },
                    modifier = Modifier.fillMaxWidth())
                val trackFlow = remember(actions, selected.id, adding, settledQuery) {
                    if (adding) actions.candidates(settledQuery) else actions.tracks(selected.id)
                }
                val tracks = trackFlow.collectAsLazyPagingItems()
                LazyColumn(Modifier.weight(1f)) {
                    if (tracks.isInitialPagingLoad()) item { CircularProgressIndicator(Modifier.padding(12.dp)) }
                    else if (tracks.isInitialPagingError()) item {
                        TextButton(onClick = tracks::retry) { Text(stringResource(R.string.remote_playlist_retry)) }
                    }
                    else if (tracks.itemCount == 0) item { Text(stringResource(R.string.playlist_add_no_matches)) }
                    items(tracks.itemCount, key = { tracks.peek(it)?.id ?: "remote-$it" }) { index ->
                        tracks[index]?.let { track ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, overflow = TextOverflow.Ellipsis)
                                }
                                TextButton(enabled = !busy, onClick = { perform {
                                    if (adding) { actions.add(selected.id, track.id); adding = false }
                                    else actions.remove(selected.id, track.id)
                                } }) { Text(stringResource(if (adding) R.string.remote_playlist_add else R.string.remote_playlist_remove)) }
                            }
                        }
                    }
                }
            }
            TextButton(enabled = !busy, onClick = onDismiss, modifier = Modifier.align(Alignment.End).navigationBarsPadding()) {
                Text(stringResource(R.string.remote_playlist_close))
            }
        }
    }
    if (nameVisible) AlertDialog(onDismissRequest = { if (!busy) nameVisible = false },
        title = { Text(stringResource(if (nameTarget == null) R.string.feature_library_new_playlist_22cdbd else R.string.remote_playlist_rename)) },
        text = { Column {
            OutlinedTextField(name, { name = it.take(80) }, enabled = !busy, singleLine = true)
            if (failed) Text(stringResource(R.string.remote_playlist_error), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(enabled = !busy && name.isNotBlank(), onClick = { perform {
            val target = nameTarget
            if (target == null) actions.create(name) else actions.rename(target.id, name)
            nameVisible = false
        } }) { Text(stringResource(R.string.remote_playlist_save)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { nameVisible = false }) { Text(stringResource(R.string.remote_playlist_close)) } })
    deleteTarget?.let { playlist -> AlertDialog(onDismissRequest = { if (!busy) deleteTarget = null },
        title = { Text(stringResource(R.string.remote_playlist_delete)) },
        text = { Column {
            Text(stringResource(R.string.remote_playlist_delete_confirm, playlist.name))
            if (failed) Text(stringResource(R.string.remote_playlist_error), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = { perform {
            actions.delete(playlist.id); selectedId = null; deleteTarget = null
        } }) { Text(stringResource(R.string.remote_playlist_delete)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { deleteTarget = null }) { Text(stringResource(R.string.remote_playlist_close)) } }) }
}
