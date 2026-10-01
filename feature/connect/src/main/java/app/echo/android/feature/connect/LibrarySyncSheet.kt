package app.echo.android.feature.connect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.connect.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LibrarySyncActions(
    val load: suspend () -> List<EchoSyncSelection>,
    val preview: suspend (List<EchoSyncSelection>) -> EchoSyncPlan,
    val apply: suspend (EchoSyncPlan) -> EchoSyncBatchResult,
    val advanced: LibraryReconcileActions? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibrarySyncSheet(pcName: String, actions: LibrarySyncActions, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<EchoSyncSelection>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var request by remember { mutableIntStateOf(0) }
    var error by remember { mutableIntStateOf(0) }
    var plan by remember { mutableStateOf<EchoSyncPlan?>(null) }
    var result by remember { mutableStateOf<EchoSyncBatchResult?>(null) }
    var operation by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun key(row: EchoSyncSelection) = "${row.fromPc}:${row.collection.key}"
    fun recordError(exception: Exception) {
        error = when (exception) {
            is EchoSyncUnsupportedException -> R.string.sync_unsupported
            is EchoSyncPairingRequiredException -> R.string.sync_pairing_required
            else -> R.string.sync_error
        }
    }
    LaunchedEffect(request) {
        loading = true; error = 0
        try { rows = actions.load(); selected = rows.filter { it.collection.favorites }.map(::key).toSet() }
        catch (e: CancellationException) { throw e } catch (e: Exception) { recordError(e) }
        finally { loading = false }
    }
    ModalBottomSheet(onDismissRequest = { operation?.cancel(); onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text(stringResource(R.string.sync_title), style = MaterialTheme.typography.headlineSmall)
                Text(pcName, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.sync_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.sync_matching_hint), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
            if (loading || running) item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(if (running) R.string.sync_working else R.string.sync_loading))
                if (running) TextButton(onClick = { operation?.cancel(); running = false; plan = null }) {
                    Text(stringResource(R.string.sync_cancel))
                }
            }
            if (error != 0) item {
                Text(stringResource(error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { plan = null; result = null; request++ }, enabled = !running) { Text(stringResource(R.string.sync_retry)) }
            }
            if (result != null) item {
                Text(stringResource(R.string.sync_done, result!!.matched, result!!.skipped), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.sync_close)) }
            }
            if (plan != null && result == null) item {
                HorizontalDivider()
                Text(stringResource(R.string.sync_preview, plan!!.matched, plan!!.skipped),
                    Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.sync_conflicts), style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    val captured = plan ?: return@Button
                    running = true; error = 0
                    operation = scope.launch {
                        try { result = actions.apply(captured); plan = null }
                        catch (e: CancellationException) { throw e } catch (e: Exception) { recordError(e); plan = null }
                        finally { running = false }
                    }
                }, enabled = !running, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(stringResource(R.string.sync_apply)) }
                TextButton(onClick = { plan = null }, enabled = !running) { Text(stringResource(R.string.sync_change_selection)) }
            }
            if (plan == null && result == null) {
                items(rows, key = ::key) { row ->
                    val checked = key(row) in selected
                    Row(Modifier.fillMaxWidth().clickable(enabled = !running) {
                        selected = if (checked) selected - key(row) else selected + key(row)
                    }.padding(vertical = 8.dp)) {
                        Checkbox(checked, onCheckedChange = null)
                        Column(Modifier.weight(1f).padding(top = 8.dp)) {
                            Text(if (row.collection.favorites) stringResource(R.string.sync_favorites) else row.collection.name,
                                style = MaterialTheme.typography.titleMedium)
                            Text(androidx.compose.ui.res.pluralStringResource(if (row.fromPc) R.plurals.sync_from_pc else R.plurals.sync_to_pc, row.collection.trackCount, row.collection.trackCount),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                }
                item {
                    val count = rows.filter { key(it) in selected }.sumOf { it.collection.trackCount.toLong() }
                    if (count > 10000) Text(stringResource(R.string.sync_limit), color = MaterialTheme.colorScheme.error)
                    Button(onClick = {
                        val choices = rows.filter { key(it) in selected }
                        running = true; error = 0
                        operation = scope.launch {
                            try { plan = actions.preview(choices) }
                            catch (e: CancellationException) { throw e } catch (e: Exception) { recordError(e) }
                            finally { running = false }
                        }
                    }, enabled = selected.isNotEmpty() && count <= 10000 && !running && !loading,
                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sync_preview_button)) }
                }
            }
        }
    }
}
