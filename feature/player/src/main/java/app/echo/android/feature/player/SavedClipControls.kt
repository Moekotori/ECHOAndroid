package app.echo.android.feature.player

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
import kotlinx.coroutines.launch

/** The list belongs to the current song; leaving the sheet cancels pending UI work. */
@Composable
internal fun SavedClipControls(track: EchoTrack, start: Long, end: Long, actions: TrackToolsActions,
    available: Boolean, onLoad: suspend (EchoSavedTrackClip) -> Unit) {
    val clips by remember(track.id) { actions.clips(track.id) }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember(track.id) { mutableStateOf(false) }
    var selected by remember(track.id) { mutableStateOf<EchoSavedTrackClip?>(null) }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    fun run(action: suspend () -> Unit) { scope.launch {
        busy = true; failed = false
        try { action() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true } finally { busy = false }
    } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.saved_clips_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.saved_clips_detail), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { selected = null; name = ""; editing = true },
            enabled = !busy && EchoSavedTrackClip("", track.id, "", start, end).isPlayable(track.durationMs)) {
            Text(stringResource(R.string.saved_clips_save))
        }
        if (failed) Text(stringResource(R.string.saved_clips_error), color = MaterialTheme.colorScheme.error)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) { items(clips, key = { it.id }) { clip ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(clip.label, style = MaterialTheme.typography.titleSmall)
                Text("${trackToolsTime(clip.startMs)} – ${trackToolsTime(clip.endMs)}", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { run { onLoad(clip) } }, enabled = !busy && available && clip.isPlayable(track.durationMs)) {
                        Text(stringResource(R.string.saved_clips_load))
                    }
                    TextButton(onClick = { selected = clip; name = clip.label; editing = true }, enabled = !busy) {
                        Text(stringResource(R.string.saved_clips_rename))
                    }
                    TextButton(onClick = { run { actions.deleteClip(clip.id) } }, enabled = !busy) {
                        Text(stringResource(R.string.saved_clips_delete))
                    }
                }
            }
        } }
    }
    if (editing) AlertDialog(onDismissRequest = { if (!busy) editing = false },
        title = { Text(stringResource(if (selected == null) R.string.saved_clips_save else R.string.saved_clips_rename)) },
        text = { OutlinedTextField(name, { name = it.take(100) }, singleLine = true, enabled = !busy,
            label = { Text(stringResource(R.string.saved_clips_name)) }) },
        confirmButton = { TextButton(enabled = !busy && name.isNotBlank(), onClick = {
            val clip = selected
            run {
                if (clip == null) actions.saveClip(track, name, start, end) else actions.renameClip(clip.id, name)
                editing = false
            }
        }) { Text(stringResource(R.string.saved_clips_confirm)) } },
        dismissButton = { TextButton(onClick = { editing = false }, enabled = !busy) { Text(stringResource(R.string.saved_clips_cancel)) } })
}
