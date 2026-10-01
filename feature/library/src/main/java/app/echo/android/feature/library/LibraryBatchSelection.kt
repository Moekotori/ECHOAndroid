package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LibraryBatchActions(
    val playlists: List<EchoPlaylist>,
    val favorites: suspend (List<String>, Boolean) -> Unit,
    val addToPlaylist: suspend (String, List<String>) -> Unit,
    val queue: suspend (List<EchoTrack>, Int) -> Unit,
    val tags: suspend (List<String>, EchoBatchTagPatch, (Int) -> Unit) -> EchoBatchResult,
    val writeAccess: suspend (List<EchoTrack>) -> Boolean = { true },
)

internal class LibraryBatchSelection {
    val selected = mutableStateMapOf<String, EchoTrack>()
    var locked by mutableStateOf(false)
    fun toggle(track: EchoTrack) {
        if (locked) return
        if (track.id in selected) selected.remove(track.id)
        else if (selected.size < 200) selected[track.id] = track
    }
}
internal val LocalLibraryBatchSelection = staticCompositionLocalOf<LibraryBatchSelection?> { null }

@Composable
fun LibraryBatchProvider(actions: LibraryBatchActions, content: @Composable () -> Unit) {
    val selection = remember { LibraryBatchSelection() }
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var choosingPlaylist by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    fun operate(operation: suspend (List<EchoTrack>) -> Unit) {
        val tracks = selection.selected.values.toList()
        busy = true; selection.locked = true; error = false; expanded = false
        scope.launch {
            try { operation(tracks); selection.selected.clear() }
            catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
            finally { busy = false; selection.locked = false }
        }
    }
    CompositionLocalProvider(LocalLibraryBatchSelection provides selection) {
        Column(Modifier.fillMaxSize()) {
            if (selection.selected.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { selection.selected.clear() }, enabled = !busy) { Text(stringResource(R.string.batch_clear, selection.selected.size)) }
                    Box {
                        TextButton(onClick = { expanded = true }, enabled = !busy) { Text(stringResource(R.string.batch_actions)) }
                        DropdownMenu(expanded, { expanded = false }) {
                            DropdownMenuItem({ Text(stringResource(R.string.batch_favorite)) }, { operate { actions.favorites(it.map(EchoTrack::id), true) } })
                            DropdownMenuItem({ Text(stringResource(R.string.batch_unfavorite)) }, { operate { actions.favorites(it.map(EchoTrack::id), false) } })
                            DropdownMenuItem({ Text(stringResource(R.string.batch_playlist)) }, { expanded = false; choosingPlaylist = true })
                            DropdownMenuItem({ Text(stringResource(R.string.batch_queue)) }, { operate { actions.queue(it, 0) } })
                            DropdownMenuItem({ Text(stringResource(R.string.batch_next)) }, { operate { actions.queue(it, 1) } })
                            DropdownMenuItem({ Text(stringResource(R.string.batch_next_up)) }, { operate { actions.queue(it, 2) } })
                            DropdownMenuItem({ Text(stringResource(R.string.batch_tags)) }, { expanded = false; editing = true }, enabled = selection.selected.values.all { it.source.isLocalAudioFile })
                        }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error) Text(stringResource(R.string.library_tool_error), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
                HorizontalDivider()
            }
            Box(Modifier.weight(1f)) { content() }
        }
    }
    if (editing) BatchTagEditor(selection.selected.values.toList(), actions) { editing = false; selection.selected.clear() }
    if (choosingPlaylist) AlertDialog(onDismissRequest = { choosingPlaylist = false },
        title = { Text(stringResource(R.string.batch_playlist)) }, text = {
            androidx.compose.foundation.lazy.LazyColumn { actions.playlists.filter { it.canEdit }.forEach { playlist ->
                item(key = playlist.id) { TextButton(onClick = {
                    choosingPlaylist = false; operate { actions.addToPlaylist(playlist.id, it.map(EchoTrack::id)) }
                }) { Text(playlist.name) } }
            } }
        }, confirmButton = { TextButton(onClick = { choosingPlaylist = false }) { Text(stringResource(R.string.batch_cancel)) } })
}
