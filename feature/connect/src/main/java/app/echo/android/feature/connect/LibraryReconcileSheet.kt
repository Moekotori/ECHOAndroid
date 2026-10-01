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
import kotlinx.coroutines.*

class LibraryReconcileActions(val index: suspend () -> List<EchoSyncIndexRow>, val preview: suspend (List<String>) -> List<EchoSyncDraft>,
    val apply: suspend (List<EchoSyncDraft>,Map<String,EchoSyncChoice>) -> EchoSyncApplyResult)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryReconcileSheet(actions: LibraryReconcileActions,onLegacy: () -> Unit,onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<EchoSyncIndexRow>>(emptyList()) }
    var selected by remember { mutableStateOf(setOf("favorites")) }
    var drafts by remember { mutableStateOf<List<EchoSyncDraft>?>(null) }
    val choices = remember { mutableStateMapOf<String,EchoSyncChoice>() }
    var busy by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var unsupported by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<EchoSyncApplyResult?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var operation by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(refresh) {
        busy = true; failed = false
        try { rows = actions.index() }
        catch (e: CancellationException) { throw e } catch (_: EchoSyncUnsupportedException) { unsupported = true } catch (_: Exception) { failed = true }
        finally { busy = false }
    }
    ModalBottomSheet(onDismissRequest = { operation?.cancel(); onDismiss() },sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth(),contentPadding = PaddingValues(24.dp),verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text(stringResource(R.string.reconcile_title),style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.reconcile_hint),color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) { Text(stringResource(R.string.reconcile_error),color = MaterialTheme.colorScheme.error); TextButton(onClick = { drafts = null; refresh++ },enabled = !busy) { Text(stringResource(R.string.sync_retry)) } }
                if (unsupported) Text(stringResource(R.string.sync_unsupported))
                TextButton(onClick = onLegacy,enabled = !busy) { Text(stringResource(R.string.reconcile_legacy)) }
                result?.let { Text(stringResource(R.string.reconcile_result,it.matched,it.missing))
                    if (it.keptExisting) Text(stringResource(R.string.reconcile_kept)) }
            }
            if (drafts == null && result == null) {
                items(rows,key = { it.key }) { row ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { selected = if (row.key in selected) selected - row.key else selected + row.key },verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(row.key in selected,null)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(if (row.key == "favorites") stringResource(R.string.sync_favorites) else row.name,style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.reconcile_counts,row.phoneCount,row.pcCount),style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider()
                }
                item {
                    val size = rows.filter { it.key in selected }.sumOf { it.phoneCount + it.pcCount }
                    if (size > 10000) Text(stringResource(R.string.sync_limit))
                    Button(onClick = { busy = true; failed = false; operation = scope.launch {
                        try { drafts = actions.preview(selected.toList()); choices.clear() }
                        catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
                        finally { busy = false }
                    } },enabled = !busy && selected.isNotEmpty() && size <= 10000 && !unsupported,modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sync_preview_button)) }
                }
            } else if (drafts != null) {
                items(drafts.orEmpty(),key = { it.phone.key }) { draft ->
                    Text(if (draft.phone.key == "favorites") stringResource(R.string.sync_favorites) else draft.phone.name.ifBlank { draft.pc.name },style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.reconcile_side,draft.phone.name,draft.phone.tracks.size,draft.pc.name,draft.pc.tracks.size),style = MaterialTheme.typography.bodySmall)
                    if (draft.conflict) {
                        Text(stringResource(R.string.reconcile_conflict),color = MaterialTheme.colorScheme.error)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EchoSyncChoice.entries.forEach { choice -> FilterChip(choices[draft.phone.key] == choice,{ choices[draft.phone.key] = choice },enabled = !busy,
                                label = { Text(stringResource(when(choice) { EchoSyncChoice.Phone -> R.string.reconcile_phone; EchoSyncChoice.Pc -> R.string.reconcile_pc; EchoSyncChoice.Merge -> R.string.reconcile_merge })) }) }
                        }
                    }
                    val choice = choices[draft.phone.key]
                    val change by produceState(Triple(0,0,false),draft,choice) {
                        value = withContext(Dispatchers.Default) {
                            val desired = EchoSyncReconcile.resolve(draft,choice)
                            fun removed(old: EchoSyncState,new: EchoSyncState): Int = if (!new.exists) old.tracks.size else (old.tracks.map(EchoSyncReconcile::trackKey).toSet() - new.tracks.map(EchoSyncReconcile::trackKey).toSet()).size
                            Triple(removed(draft.phone,desired.first),removed(draft.pc,desired.second),!desired.first.exists || !desired.second.exists)
                        }
                    }
                    Text(stringResource(R.string.reconcile_changes,change.first,change.second))
                    if (change.third) Text(stringResource(R.string.reconcile_delete),color = MaterialTheme.colorScheme.error)
                    HorizontalDivider()
                }
                item {
                    Button(onClick = { val captured = drafts.orEmpty(); val resolved = choices.toMap(); busy = true; failed = false; operation = scope.launch {
                        try { result = actions.apply(captured,resolved); drafts = null }
                        catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true; drafts = null }
                        finally { busy = false }
                    } },enabled = !busy && drafts.orEmpty().all { !it.conflict || choices.containsKey(it.phone.key) },modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reconcile_apply)) }
                }
            }
        }
    }
}
