package app.echo.android.feature.library

import androidx.compose.foundation.clickable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryRepairSheet(actions: LibraryExperienceActions, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<EchoLibraryRepairItem>>(emptyList()) }
    var inspecting by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var request by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<EchoLibraryRepairItem?>(null) }
    var candidates by remember { mutableStateOf<List<EchoTrack>>(emptyList()) }
    var candidate by remember { mutableStateOf<EchoTrack?>(null) }
    var finding by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var repaired by remember { mutableIntStateOf(0) }
    LaunchedEffect(request) {
        inspecting = true; error = false
        try { rows = actions.inspect() }
        catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
        finally { inspecting = false }
    }
    LaunchedEffect(selected?.track?.id) {
        candidate = null; candidates = emptyList()
        val row = selected ?: return@LaunchedEffect
        finding = true
        try { candidates = actions.candidates(row.track) }
        catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
        finally { finding = false }
    }
    ModalBottomSheet(onDismissRequest = { if (!applying) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(stringResource(R.string.repair_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.repair_hint), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onDismiss(); actions.rescanFolder() }, enabled = !applying) {
                    Text(stringResource(R.string.repair_scan_folder))
                }
                Text(stringResource(R.string.repair_scan_folder_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (repaired > 0) Text(androidx.compose.ui.res.pluralStringResource(R.plurals.repair_done, repaired, repaired), Modifier.padding(top = 12.dp))
            }
            if (inspecting) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.repair_inspecting)) }
            if (error) item {
                Text(stringResource(R.string.library_tool_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { selected = null; request++ }, enabled = !inspecting && !applying) { Text(stringResource(R.string.library_tool_retry)) }
            }
            if (selected == null) {
                if (!inspecting && !error && rows.isEmpty()) item {
                    Text(stringResource(R.string.repair_all_available), style = MaterialTheme.typography.titleMedium)
                }
                if (rows.size == 500) item { Text(stringResource(R.string.repair_limit)) }
                items(rows, key = { it.track.id }) { row ->
                    Column(Modifier.fillMaxWidth().clickable { selected = row }.padding(vertical = 8.dp)) {
                        Text(row.track.title, style = MaterialTheme.typography.titleMedium)
                        Text(row.track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(row.location, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
            } else {
                item {
                    TextButton(onClick = { selected = null }, enabled = !applying) { Text(stringResource(R.string.repair_back)) }
                    Text(selected!!.track.title, style = MaterialTheme.typography.titleLarge)
                    Text(selected!!.track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.repair_choose), Modifier.padding(top = 12.dp))
                    if (finding) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                items(candidates, key = { it.id }) { track ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !applying) { candidate = track }) {
                        RadioButton(candidate?.id == track.id, { candidate = track }, enabled = !applying)
                        Column(Modifier.weight(1f).padding(top = 8.dp)) {
                            Text(track.title, style = MaterialTheme.typography.bodyLarge)
                            Text(listOfNotNull(track.artist, track.album).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = { scope.launch {
                        finding = true
                        try { actions.chooseFile()?.let { candidate = it } }
                        catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
                        finally { finding = false }
                    } }, enabled = !finding && !applying, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.repair_choose_file)) }
                }
                candidate?.let { target -> item {
                    HorizontalDivider()
                    Text(stringResource(R.string.repair_preview), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
                    Text(target.title)
                    Text(listOfNotNull(target.artist, target.album).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(target.uri, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.repair_preserve), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        val old = selected ?: return@Button
                        applying = true; error = false
                        scope.launch {
                            try { actions.relink(old.track.id, target); rows = rows.filterNot { it.track.id == old.track.id }; repaired++; selected = null }
                            catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
                            finally { applying = false }
                        }
                    }, enabled = !applying, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text(stringResource(if (applying) R.string.library_tool_saving else R.string.repair_apply))
                    }
                } }
            }
        }
    }
}
