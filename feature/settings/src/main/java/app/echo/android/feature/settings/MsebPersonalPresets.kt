package app.echo.android.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MsebPresetActions(
    val presets: List<EchoMsebUserPreset> = emptyList(),
    val save: suspend (String, List<Float>, String?) -> Unit = { _, _, _ -> },
    val rename: suspend (String, String) -> Unit = { _, _ -> },
    val delete: suspend (String) -> Unit = {},
    val importPresets: suspend (List<EchoMsebUserPreset>) -> Unit = {},
)

@Composable
internal fun MsebPersonalPresets(state: EchoMsebState, actions: MsebPresetActions, enabled: Boolean,
    onApply: (EchoMsebUserPreset) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<Pair<String?, String>?>(null) }
    var pendingDelete by remember { mutableStateOf<EchoMsebUserPreset?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<List<EchoMsebUserPreset>?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentActions by rememberUpdatedState(actions)
    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) perform {
            val text = EchoMsebUserPresets.encode(currentActions.presets)
            withContext(Dispatchers.IO) {
                checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(text) }
            }
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) perform {
            importing = withContext(Dispatchers.IO) {
                val bytes = checkNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= EchoMsebUserPresets.MaxImportBytes)
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                require(bytes.size <= EchoMsebUserPresets.MaxImportBytes)
                EchoMsebUserPresets.decode(bytes.toString(Charsets.UTF_8))
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(R.string.mseb_personal_count, actions.presets.size, EchoMsebUserPresets.MaxCount))
            }
            TextButton(enabled = !busy && actions.presets.size < EchoMsebUserPresets.MaxCount,
                onClick = { editor = null to "" }) { Text(stringResource(R.string.mseb_personal_save)) }
        }
        if (expanded) {
            actions.presets.forEach { preset -> key(preset.id) {
                val matches = preset.values == state.normalized.values
                Column(Modifier.fillMaxWidth()) {
                    Text(preset.name, style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(enabled = enabled && !busy && !matches, onClick = { onApply(preset) }) {
                            Text(stringResource(if (matches) R.string.mseb_personal_current else R.string.mseb_personal_apply))
                        }
                        TextButton(enabled = !busy, onClick = { editor = preset.id to preset.name }) {
                            Text(stringResource(R.string.mseb_personal_rename))
                        }
                        TextButton(enabled = !busy && !matches, onClick = {
                            perform { currentActions.save(preset.name, state.normalized.values, preset.id) }
                        }) { Text(stringResource(R.string.mseb_personal_update)) }
                        TextButton(enabled = !busy, onClick = { pendingDelete = preset }) {
                            Text(stringResource(R.string.mseb_personal_delete))
                        }
                    }
                }
            } }
            Row {
                TextButton(enabled = !busy, onClick = { importFile.launch(arrayOf("text/*", "application/octet-stream")) }) {
                    Text(stringResource(R.string.mseb_personal_import))
                }
                TextButton(enabled = !busy && actions.presets.isNotEmpty(), onClick = { export.launch("echo-mseb.txt") }) {
                    Text(stringResource(R.string.mseb_personal_export))
                }
            }
        }
    }
    editor?.let { (id, initial) ->
        var name by remember(id, initial) { mutableStateOf(initial) }
        AlertDialog(onDismissRequest = { editor = null }, title = { Text(stringResource(R.string.mseb_personal_name)) },
            text = { OutlinedTextField(name, { name = it.take(EchoMsebUserPresets.MaxNameLength) }, singleLine = true) },
            confirmButton = { TextButton(enabled = !busy && name.isNotBlank(), onClick = {
                val chosen = name
                perform {
                    if (id == null) currentActions.save(chosen, state.normalized.values, null)
                    else currentActions.rename(id, chosen)
                    editor = null; expanded = true
                }
            }) { Text(stringResource(R.string.mseb_personal_save)) } },
            dismissButton = { TextButton(onClick = { editor = null }) { Text(stringResource(R.string.mseb_personal_cancel)) } })
    }
    pendingDelete?.let { preset ->
        AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text(preset.name) },
            text = { Text(stringResource(R.string.mseb_personal_delete_confirm)) },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                perform { currentActions.delete(preset.id); pendingDelete = null }
            }) { Text(stringResource(R.string.mseb_personal_delete)) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.mseb_personal_cancel)) } })
    }
    importing?.let { presets ->
        val fits = actions.presets.size + presets.size <= EchoMsebUserPresets.MaxCount
        AlertDialog(onDismissRequest = { importing = null }, title = { Text(stringResource(R.string.mseb_personal_import)) },
            text = { Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(if (fits) R.string.mseb_personal_import_note else R.string.mseb_personal_full))
                presets.forEach { Text(it.name) }
            } },
            confirmButton = { TextButton(enabled = fits && !busy, onClick = {
                perform { currentActions.importPresets(presets); importing = null; expanded = true }
            }) { Text(stringResource(R.string.mseb_personal_import)) } },
            dismissButton = { TextButton(onClick = { importing = null }) { Text(stringResource(R.string.mseb_personal_cancel)) } })
    }
    if (failed) AlertDialog(onDismissRequest = { failed = false }, text = { Text(stringResource(R.string.mseb_personal_failed)) },
        confirmButton = { TextButton(onClick = { failed = false }) { Text(stringResource(R.string.mseb_personal_close)) } })
}
