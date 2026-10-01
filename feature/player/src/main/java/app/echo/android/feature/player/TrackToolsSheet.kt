package app.echo.android.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.*
import app.echo.android.model.playback.EchoAbLoopState
import app.echo.android.model.lyrics.EchoLyrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class TrackToolsActions(
    val bookmarks: (String) -> Flow<List<EchoTrackBookmark>>,
    val saveBookmark: suspend (EchoTrack, Long, String) -> Unit,
    val deleteBookmark: suspend (String) -> Unit,
    val setLoop: suspend (String?, Long, Long) -> Boolean,
)

internal fun trackToolsTime(positionMs: Long): String {
    val seconds = positionMs.coerceAtLeast(0) / 1000
    return "%d:%02d.%03d".format(seconds / 60, seconds % 60, positionMs.coerceAtLeast(0) % 1000)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackToolsSheet(track: EchoTrack, position: () -> Long, loop: EchoAbLoopState,
    lyrics: EchoLyrics?, actions: TrackToolsActions, onSeek: (Long) -> Unit, onDismiss: () -> Unit,
    loopAvailable: Boolean = true) {
    val bookmarks by remember(track.id) { actions.bookmarks(track.id) }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var a by remember(track.id) { mutableLongStateOf(if (loop.active && loop.trackId == track.id) loop.startMs
        else position().coerceIn(0, (track.durationMs - 500).coerceAtLeast(0))) }
    var b by remember(track.id) { mutableLongStateOf(if (loop.active && loop.trackId == track.id) loop.endMs
        else (a + 15_000).coerceAtMost(track.durationMs.coerceAtLeast(0))) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf<Long?>(null) }
    var label by remember { mutableStateOf("") }
    var sharing by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().imePadding(), contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(stringResource(R.string.track_tools_title), style = MaterialTheme.typography.headlineSmall)
                Text(track.title, style = MaterialTheme.typography.titleMedium)
                Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Text(stringResource(R.string.track_loop_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.track_loop_hint), style = MaterialTheme.typography.bodySmall)
                if (!loopAvailable) Text(stringResource(R.string.track_loop_external), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (loop.active && loop.trackId == track.id) Text(stringResource(R.string.track_loop_active,
                    trackToolsTime(loop.startMs), trackToolsTime(loop.endMs)), color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { a = position().coerceIn(0, track.durationMs.coerceAtLeast(0)) }, enabled = !busy && loopAvailable) {
                        Text(stringResource(R.string.track_loop_mark_a, trackToolsTime(a)))
                    }
                    TextButton(onClick = { b = position().coerceIn(0, track.durationMs.coerceAtLeast(0)) }, enabled = !busy && loopAvailable) {
                        Text(stringResource(R.string.track_loop_mark_b, trackToolsTime(b)))
                    }
                }
                if (track.durationMs > 0) RangeSlider(
                    value = a.toFloat().coerceAtMost(b.toFloat())..b.toFloat().coerceAtLeast(a.toFloat()),
                    onValueChange = { a = it.start.toLong(); b = it.endInclusive.toLong() },
                    valueRange = 0f..track.durationMs.toFloat(), enabled = !busy && loopAvailable)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { busy = true; scope.launch {
                        try { error = !actions.setLoop(track.id, a, b) }
                        catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
                        finally { busy = false }
                    } }, enabled = b - a >= 500 && !busy && track.durationMs > 0 && loopAvailable) { Text(stringResource(R.string.track_loop_start)) }
                    if (loop.active) TextButton(onClick = { busy = true; scope.launch {
                        try { error = !actions.setLoop(null, 0, 0) }
                        catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
                        finally { busy = false }
                    } }, enabled = !busy) { Text(stringResource(R.string.track_loop_stop)) }
                }
            }
            item {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.track_bookmarks), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { adding = position(); label = trackToolsTime(adding ?: 0) }, enabled = !busy && bookmarks.size < 200) {
                        Text(stringResource(R.string.track_bookmark_add))
                    }
                }
                if (bookmarks.isEmpty()) Text(stringResource(R.string.track_bookmark_empty), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(bookmarks, key = { it.id }) { bookmark ->
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f).clickable { onSeek(bookmark.positionMs) }.padding(vertical = 8.dp)) {
                        Text(bookmark.label, style = MaterialTheme.typography.titleMedium)
                        if (bookmark.label != trackToolsTime(bookmark.positionMs)) Text(trackToolsTime(bookmark.positionMs), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { scope.launch {
                        try { actions.deleteBookmark(bookmark.id) }
                        catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
                    } }) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.track_bookmark_delete)) }
                }
            }
            item {
                HorizontalDivider()
                OutlinedButton(onClick = { sharing = true }, enabled = lyrics?.lines?.any { it.text.isNotBlank() } == true,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text(stringResource(R.string.lyrics_card_title)) }
                if (lyrics == null) Text(stringResource(R.string.lyrics_card_missing), style = MaterialTheme.typography.bodySmall)
                if (error) Text(stringResource(R.string.track_tools_error), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    adding?.let { time -> AlertDialog(onDismissRequest = { if (!busy) adding = null },
        title = { Text(stringResource(R.string.track_bookmark_add)) },
        text = { OutlinedTextField(label, { label = it.take(100) }, label = { Text(stringResource(R.string.track_bookmark_name)) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { busy = true; scope.launch {
            try { actions.saveBookmark(track, time, label); adding = null }
            catch (e: CancellationException) { throw e } catch (_: Exception) { error = true; adding = null }
            finally { busy = false }
        } }, enabled = label.isNotBlank() && !busy) { Text(stringResource(R.string.track_tools_save)) } },
        dismissButton = { TextButton(onClick = { adding = null }, enabled = !busy) { Text(stringResource(R.string.track_tools_cancel)) } }) }
    if (sharing && lyrics != null) LyricsCardSheet(track, lyrics) { sharing = false }
}
