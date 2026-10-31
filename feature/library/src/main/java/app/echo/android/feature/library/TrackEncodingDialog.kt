package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun TrackEncodingDialog(track: EchoTrack, tools: LibraryMediaTools, onDismiss: () -> Unit) {
    var charset by remember(track.id) { mutableStateOf<String?>(null) }
    var preview by remember(track.id) { mutableStateOf<EchoEncodingPreview?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf(false) }
    var folder by remember { mutableStateOf<List<EchoFolderEncodingItem>?>(null) }
    var folderResult by remember { mutableStateOf<EchoFolderEncodingResult?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(track.id, charset, reload) {
        preview = null; failed = false; saved = false
        try { preview = tools.previewEncoding(track, charset) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { action(); saved = true }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.encoding_title)) },
        text = { Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.encoding_note))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = charset == null, onClick = { charset = null }, enabled = !busy,
                    label = { Text(stringResource(R.string.encoding_auto)) })
                EchoTextEncodings.names.forEach { name ->
                    FilterChip(selected = charset == name, onClick = { charset = name }, enabled = !busy, label = { Text(name) })
                }
            }
            preview?.let { value ->
                Text(value.title, style = MaterialTheme.typography.titleMedium)
                Text(value.artist)
                value.album?.let { Text(it) }
                value.albumArtist?.let { Text(it) }
                if (value.canRestore) TextButton(enabled = !busy, onClick = { perform {
                    tools.restoreEncoding(track); charset = null; reload++
                } }) { Text(stringResource(R.string.encoding_restore)) }
                if (charset != null) TextButton(enabled = !busy, onClick = { perform {
                    folderResult = null
                    folder = tools.previewFolder(track, charset!!)
                } }) { Text(stringResource(R.string.encoding_folder)) }
            }
            if (preview == null && !failed || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) {
                Text(stringResource(R.string.encoding_failed), color = MaterialTheme.colorScheme.error)
                TextButton(enabled = !busy, onClick = { reload++ }) { Text(stringResource(R.string.media_tools_retry)) }
            }
            if (saved) Text(stringResource(R.string.encoding_saved))
        } }, confirmButton = {
            TextButton(enabled = preview != null && charset != null && !busy && !saved, onClick = {
                val value = preview ?: return@TextButton
                perform { tools.applyEncoding(track, value); preview = tools.previewEncoding(track, charset) }
            }) { Text(stringResource(R.string.encoding_apply)) }
        }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.media_tools_close)) } })
    folder?.let { items ->
        AlertDialog(onDismissRequest = { if (!busy) folder = null }, title = { Text(stringResource(R.string.encoding_folder)) },
            text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.encoding_folder_note))
                items.forEach { item ->
                    val value = item.preview
                    Text(if (value == null) stringResource(R.string.encoding_folder_skip, item.title)
                        else "${item.title} → ${value.title}", style = MaterialTheme.typography.bodySmall)
                }
                folderResult?.let { Text(stringResource(R.string.encoding_folder_result, it.applied, it.failed)) }
            } }, confirmButton = { TextButton(enabled = !busy && folderResult == null && items.any { it.preview != null }, onClick = {
                perform { folderResult = tools.applyFolder(items) }
            }) { Text(stringResource(R.string.encoding_apply_folder)) } }, dismissButton = {
                TextButton(enabled = !busy, onClick = { folder = null; reload++ }) { Text(stringResource(R.string.media_tools_close)) }
            })
    }
}
