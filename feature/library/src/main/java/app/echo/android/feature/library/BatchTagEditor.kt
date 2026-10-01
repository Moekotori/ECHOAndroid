package app.echo.android.feature.library

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

private data class BatchTagField(val label: Int, val selected: Boolean = false, val value: String = "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BatchTagEditor(tracks: List<EchoTrack>, actions: LibraryBatchActions, onDismiss: () -> Unit) {
    val fields = remember { mutableStateListOf(BatchTagField(R.string.batch_artist), BatchTagField(R.string.batch_album),
        BatchTagField(R.string.batch_album_artist), BatchTagField(R.string.batch_composer), BatchTagField(R.string.batch_year), BatchTagField(R.string.batch_genre)) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<EchoBatchResult?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun patch(): EchoBatchTagPatch {
        fun value(index: Int) = fields[index].value.trim().takeIf { fields[index].selected }
        return EchoBatchTagPatch(value(0), value(1), value(2), value(3), value(4)?.ifBlank { "0" }?.toIntOrNull(), value(5))
    }
    val valid = fields.any { it.selected } && (!fields[0].selected || fields[0].value.isNotBlank()) &&
        (!fields[4].selected || fields[4].value.isBlank() || fields[4].value.toIntOrNull()?.let { it in 1..9999 } == true)
    ModalBottomSheet(onDismissRequest = { operation?.cancel(); onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().imePadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.batch_tags), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.batch_tag_hint, tracks.size))
                if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.batch_progress, progress, tracks.size)) }
                result?.let { Text(stringResource(R.string.batch_result, it.completed, it.failed, it.fileWritesFailed)) }
                if (error) Text(stringResource(R.string.library_tool_error), color = MaterialTheme.colorScheme.error)
            }
            items(fields.indices.toList(), key = { it }) { index ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(fields[index].selected, { fields[index] = fields[index].copy(selected = it) }, enabled = !busy)
                    OutlinedTextField(fields[index].value, { fields[index] = fields[index].copy(value = it.take(300)) },
                        label = { Text(stringResource(fields[index].label)) }, enabled = fields[index].selected && !busy,
                        singleLine = true, modifier = Modifier.weight(1f))
                }
            }
            item { Button(onClick = { confirm = true }, enabled = valid && !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.batch_preview)) } }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(stringResource(R.string.batch_confirm, tracks.size)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fields.filter { it.selected }.forEach { Text("${stringResource(it.label)}: ${it.value.ifBlank { stringResource(R.string.batch_empty) }}") }
            Text(tracks.take(5).joinToString("\n") { it.title })
        } }, confirmButton = { TextButton(onClick = {
            confirm = false; busy = true; progress = 0; error = false
            val draft = patch()
            operation = scope.launch {
                try {
                    if (actions.writeAccess(tracks)) result = actions.tags(tracks.map(EchoTrack::id), draft) { progress = it }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
                finally { busy = false }
            }
        }) { Text(stringResource(R.string.batch_apply)) } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.batch_cancel)) } })
}
