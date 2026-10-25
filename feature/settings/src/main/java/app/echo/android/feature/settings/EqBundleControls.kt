package app.echo.android.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.*
import kotlinx.coroutines.*

@Composable
internal fun EqBundleControls(presets: List<EchoEqualizerUserPreset>, enabled: Boolean,
    onImport: suspend (List<EchoEqualizerUserPreset>, EchoEqImportConflict) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var imported by remember { mutableStateOf<List<EchoEqualizerUserPreset>?>(null) }
    var exportSelection by remember { mutableStateOf<Set<String>?>(null) }
    var exportText by remember { mutableStateOf<String?>(null) }
    var conflict by remember { mutableStateOf(EchoEqImportConflict.Add) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    fun perform(block: suspend () -> Unit) {
        if (busy) return
        busy = true; failed = false; success = false
        scope.launch { try { block() } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed = true } finally { busy = false } }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = exportText
        exportText = null
        if (uri != null && text == null) failed = true
        if (uri != null && text != null) perform { withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("Cannot export")
        }; success = true }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) perform { imported = withContext(Dispatchers.IO) {
            val raw = context.contentResolver.openInputStream(uri)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(count > 0) { "No progress reading bundle" }
                    require(output.size() + count <= EchoEqBundleCodec.MaxBytes)
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            } ?: error("Cannot read bundle")
            EchoEqBundleCodec.decode(raw)
        } }
    }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = enabled && !busy, onClick = { pick.launch(arrayOf("text/*", "application/octet-stream")) }) {
                Text(stringResource(R.string.eq_bundle_import)) }
            TextButton(enabled = enabled && !busy && exportText == null && presets.isNotEmpty(), onClick = { exportSelection = presets.map { it.id }.toSet() }) {
                Text(stringResource(R.string.eq_bundle_export)) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (failed) Text(stringResource(R.string.learning_operation_failed), color = MaterialTheme.colorScheme.error)
        if (success) Text(stringResource(R.string.learning_operation_done))
    }
    exportSelection?.let { selection ->
        AlertDialog(onDismissRequest = { exportSelection = null }, title = { Text(stringResource(R.string.eq_bundle_export)) },
            text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                TextButton(onClick = { exportSelection = if (selection.size == presets.size) emptySet() else presets.map { it.id }.toSet() }) {
                    Text(stringResource(R.string.eq_bundle_select_all)) }
                presets.forEach { preset -> Row {
                    Checkbox(checked = preset.id in selection, onCheckedChange = { checked ->
                        exportSelection = if (checked) selection + preset.id else selection - preset.id })
                    Text(preset.name, Modifier.padding(top = 12.dp))
                } }
            } }, confirmButton = { TextButton(enabled = selection.isNotEmpty(), onClick = {
                perform {
                    exportText = withContext(Dispatchers.Default) { EchoEqBundleCodec.encode(presets.filter { it.id in selection }) }
                    exportSelection = null
                    export.launch("ECHO-presets.echoeq")
                }
            }) { Text(stringResource(R.string.eq_bundle_export)) } })
    }
    imported?.let { incoming ->
        val conflicts = remember(incoming, presets) {
            val names = presets.map { it.name }.toMutableList()
            incoming.count { row -> val match = names.any { it.equals(row.name, true) }; names += row.name; match }
        }
        val fits by produceState(false, incoming, presets, conflict) {
            value = withContext(Dispatchers.Default) { runCatching { EchoEqBundleCodec.merge(presets, incoming, conflict, 0) }.isSuccess }
        }
        AlertDialog(onDismissRequest = { if (!busy) imported = null }, title = { Text(stringResource(R.string.eq_bundle_import)) },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(incoming.joinToString("\n") { it.name }, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.eq_bundle_preview, incoming.size, conflicts, presets.size, EchoEqualizerUserPresets.MaxCount))
                EchoEqImportConflict.entries.forEach { policy -> Row {
                    RadioButton(selected = policy == conflict, enabled = !busy, onClick = { conflict = policy })
                    Text(stringResource(when (policy) {
                        EchoEqImportConflict.Add -> R.string.eq_bundle_add
                        EchoEqImportConflict.Skip -> R.string.eq_bundle_skip
                        EchoEqImportConflict.Replace -> R.string.eq_bundle_replace
                    }), Modifier.padding(top = 12.dp))
                } }
                if (!fits) Text(stringResource(R.string.eq_bundle_capacity), color = MaterialTheme.colorScheme.error)
                if (failed) Text(stringResource(R.string.learning_operation_failed), color = MaterialTheme.colorScheme.error)
            } }, confirmButton = { TextButton(enabled = !busy && fits, onClick = { perform { onImport(incoming, conflict); imported = null; success = true } }) {
                Text(stringResource(R.string.eq_bundle_confirm)) } })
    }
}
