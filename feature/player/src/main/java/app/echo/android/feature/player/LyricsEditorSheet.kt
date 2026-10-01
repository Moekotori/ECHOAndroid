package app.echo.android.feature.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.lyrics.EchoLyricsEditing
import app.echo.android.model.lyrics.*
import kotlinx.coroutines.*

private data class LyricsEditRow(val original: EchoLyricLine, val time: String, val text: String, val translation: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsEditorSheet(lyrics: EchoLyrics, position: () -> Long, onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit, onSave: suspend (EchoLyrics) -> Unit, onDismiss: () -> Unit) {
    val rows = remember(lyrics) { lyrics.lines.take(2000).map { LyricsEditRow(it, EchoLyricsEditing.timestamp(it.startMs), it.text, it.translation.orEmpty()) }.toMutableStateList() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberLazyListState()
    var active by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf<String?>(null) }
    fun snapshot(): EchoLyrics {
        require(lyrics.lines.size <= 2000)
        val lines = rows.map { row ->
            val time = EchoLyricsEditing.parseTimestamp(row.time) ?: error("Invalid time")
            val delta = if (row.original.startMs >= 0 && time >= 0) time - row.original.startMs else 0
            row.original.copy(startMs = time, text = row.text, translation = row.translation.takeIf(String::isNotBlank),
                words = if (row.text == row.original.text && time >= 0) row.original.words.map { it.copy(startMs = (it.startMs + delta).coerceAtLeast(0), endMs = it.endMs?.let { end -> (end + delta).coerceAtLeast(0) }) } else emptyList())
        }
        return EchoLyricsEditing.finish(lyrics, lines)
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = exportText; exportText = null
        if (uri != null && text != null) scope.launch {
            try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("Could not write") }; saved = true }
            catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
        }
    }
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).imePadding()) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(stringResource(R.string.lyrics_edit_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.lyrics_edit_hint), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onPlayPause) { Text(stringResource(R.string.lyrics_edit_play)) }
                    TextButton(onClick = {
                        if (rows.isNotEmpty()) {
                            val index = active.coerceIn(rows.indices)
                            rows[index] = rows[index].copy(time = EchoLyricsEditing.timestamp(position()))
                            active = (index + 1).coerceAtMost(rows.lastIndex)
                            scope.launch { scroll.scrollToItem((active + 1).coerceAtMost(rows.size)) }
                        }
                    }, enabled = !saving && rows.isNotEmpty()) { Text(stringResource(R.string.lyrics_edit_mark)) }
                    TextButton(onClick = { if (rows.size < 2000) rows.add(LyricsEditRow(EchoLyricLine(-1, text = ""), "", "", "")) }, enabled = !saving) { Text(stringResource(R.string.lyrics_edit_add)) }
                }
            }
            LazyColumn(Modifier.weight(1f), state = scroll, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(rows.size, key = { it }) { index -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { active = index; EchoLyricsEditing.parseTimestamp(rows[index].time)?.takeIf { it >= 0 }?.let(onSeek) }) {
                            Text(stringResource(if (active == index) R.string.lyrics_edit_active else R.string.lyrics_edit_line, index + 1))
                        }
                        TextButton(onClick = { rows.removeAt(index); active = active.coerceAtMost((rows.size - 1).coerceAtLeast(0)) }, enabled = !saving) { Text(stringResource(R.string.lyrics_edit_delete)) }
                    }
                    OutlinedTextField(rows[index].time, { rows[index] = rows[index].copy(time = it.take(14)); active = index }, label = { Text(stringResource(R.string.lyrics_edit_time)) }, singleLine = true, enabled = !saving)
                    OutlinedTextField(rows[index].text, { rows[index] = rows[index].copy(text = it.take(3000)) }, label = { Text(stringResource(R.string.lyrics_edit_text)) }, modifier = Modifier.fillMaxWidth(), enabled = !saving)
                    OutlinedTextField(rows[index].translation, { rows[index] = rows[index].copy(translation = it.take(3000)) }, label = { Text(stringResource(R.string.lyrics_edit_translation)) }, modifier = Modifier.fillMaxWidth(), enabled = !saving)
                    HorizontalDivider()
                } }
            }
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                if (error) Text(stringResource(R.string.lyrics_edit_error), color = MaterialTheme.colorScheme.error)
                if (saved) Text(stringResource(R.string.lyrics_edit_saved), color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { error = false; scope.launch {
                        saving = true
                        try { val draft = withContext(Dispatchers.Default) { snapshot() }; onSave(draft); saved = true }
                        catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
                        finally { saving = false }
                    } }, enabled = rows.isNotEmpty() && !saving) { Text(stringResource(R.string.lyrics_edit_save)) }
                    OutlinedButton(onClick = { error = false; scope.launch {
                        try { exportText = withContext(Dispatchers.Default) { EchoLyricsEditing.toLrc(snapshot()) }; export.launch("echo-lyrics.lrc") }
                        catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
                    } }, enabled = !saving) { Text(stringResource(R.string.lyrics_edit_export)) }
                }
            }
        }
    }
}
