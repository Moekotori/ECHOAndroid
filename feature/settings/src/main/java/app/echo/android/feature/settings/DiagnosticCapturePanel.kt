package app.echo.android.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DiagnosticCaptureActions(val enabled: StateFlow<Boolean>, val start: () -> Unit, val stop: () -> Unit,
    val clear: suspend () -> Unit, val export: suspend (OutputStream) -> Unit)

@Composable
internal fun DiagnosticCapturePanel(actions: DiagnosticCaptureActions) {
    val enabled by actions.enabled.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    fun perform(block: suspend () -> Unit) {
        if (busy) return
        busy = true; failed = false
        scope.launch { try { block() } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed = true } finally { busy = false } }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) perform { withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { actions.export(it) } ?: error("Cannot open export")
        } }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(R.string.capture_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.capture_detail), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !busy, onClick = { if (enabled) actions.stop() else actions.start() }) {
                Text(stringResource(if (enabled) R.string.capture_stop else R.string.capture_start)) }
            TextButton(enabled = !busy, onClick = { export.launch("ECHO-diagnostic-capture.zip") }) {
                Text(stringResource(R.string.capture_export)) }
            TextButton(enabled = !busy, onClick = { perform { actions.clear() } }) {
                Text(stringResource(R.string.capture_clear)) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (failed) Text(stringResource(R.string.learning_operation_failed), color = MaterialTheme.colorScheme.error)
    }
}
