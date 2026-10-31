package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoModalSheet
import app.echo.android.model.library.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NavidromePlaylistDialog(actions: NavidromeActions, id: String, onDismiss: () -> Unit) {
    var original by remember(id) { mutableStateOf<NavidromePlaylistSettings?>(null) }
    var name by remember(id) { mutableStateOf("") }
    var comment by remember(id) { mutableStateOf("") }
    var public by remember(id) { mutableStateOf(false) }
    var songs by remember(id) { mutableStateOf<List<NavidromeEntry>>(emptyList()) }
    var loading by remember(id) { mutableStateOf(true) }
    var failed by remember(id) { mutableStateOf(false) }
    var busy by remember(id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(actions, id, attempt) {
        loading = true; failed = false
        try {
            val data = actions.playlist(id)
            original = data; name = data.name; comment = data.description; public = data.public; songs = data.songs
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    val writable = original?.writable == true && !busy && !loading
    EchoModalSheet(onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !busy })) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.nav_playlist_settings), style = MaterialTheme.typography.titleLarge)
            if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) TextButton(onClick = { attempt++ }, enabled = !busy) { Text(stringResource(R.string.nav_error)) }
            if (original != null && original?.writable == false) Text(stringResource(R.string.nav_read_only), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(name, { name = it.take(80) }, enabled = writable, singleLine = true,
                label = { Text(stringResource(R.string.nav_name)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(comment, { comment = it.take(4000) }, enabled = writable, maxLines = 3,
                label = { Text(stringResource(R.string.nav_comment)) }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.nav_public), Modifier.weight(1f))
                Switch(checked = public, onCheckedChange = { public = it }, enabled = writable)
            }
            TextButton(enabled = !loading && !busy && songs.isNotEmpty(), onClick = {
                try { actions.playQueue(songs, 0) } catch (_: Exception) { failed = true }
            }) { Text(stringResource(R.string.nav_play_playlist)) }
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(songs, key = { index, song -> "$index:${song.id}" }) { index, song ->
                    Column(Modifier.fillMaxWidth()) {
                        TextButton(enabled = !busy, onClick = {
                            try { actions.playQueue(songs, index) } catch (_: Exception) { failed = true }
                        }) { Text("${index + 1}. ${song.name}", style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        Row {
                            TextButton(enabled = writable && index > 0, onClick = { songs = songs.toMutableList().apply { add(index - 1, removeAt(index)) } }) {
                                Text(stringResource(R.string.nav_move_up))
                            }
                            TextButton(enabled = writable && index < songs.lastIndex, onClick = { songs = songs.toMutableList().apply { add(index + 1, removeAt(index)) } }) {
                                Text(stringResource(R.string.nav_move_down))
                            }
                            TextButton(enabled = writable, onClick = { songs = songs.filterIndexed { i, _ -> i != index } }) {
                                Text(stringResource(R.string.remote_playlist_remove))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().navigationBarsPadding(), horizontalArrangement = Arrangement.End) {
                TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.remote_playlist_close)) }
                TextButton(enabled = writable && name.isNotBlank(), onClick = {
                    busy = true; failed = false
                    scope.launch {
                        try {
                            val ids = songs.map { it.id }.takeUnless { it == original?.songs?.map { row -> row.id } }
                            actions.execute(NavidromeCommand.Playlist(id, name, comment, public, ids)); onDismiss()
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failed = true }
                        finally { busy = false }
                    }
                }) { Text(stringResource(R.string.remote_playlist_save)) }
            }
        }
    }
}
