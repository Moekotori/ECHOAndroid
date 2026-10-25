package app.echo.android.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import app.echo.android.design.EchoModalSheet
import app.echo.android.design.EchoSheetHeader
import app.echo.android.design.displayArtist
import app.echo.android.design.displayTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val MaxSelection = 500

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HiddenTracksSheet(actions: LibraryHiddenTrackActions, onDismiss: () -> Unit) {
    val pages = remember(actions) { actions.pages() }.collectAsLazyPagingItems()
    val total by actions.count.collectAsState(initial = 0)
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var selecting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var restored by remember { mutableStateOf<Int?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    var selectionLimit by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun perform(block: suspend () -> Int) {
        if (busy) return
        busy = true; failed = false; restored = null
        scope.launch {
            try {
                restored = block()
                selected.clear(); selecting = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    EchoModalSheet(onDismissRequest = { if (!busy) onDismiss() },
        sheetGesturesEnabled = !busy,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !busy })) {
        // Consume Back before dismissing the sheet while selecting or applying a batch.
        BackHandler(enabled = (selecting || busy) && !confirmAll) {
            if (!busy) { selected.clear(); selecting = false; selectionLimit = false }
        }
        EchoSheetHeader(title = stringResource(R.string.hidden_track_title))
        Text(stringResource(R.string.hidden_track_note), Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            TextButton(enabled = !busy && total > 0, onClick = {
                selecting = !selecting; selected.clear(); selectionLimit = false
            }) { Text(stringResource(if (selecting) R.string.hidden_track_cancel else R.string.hidden_restore_select)) }
            if (selecting) {
                TextButton(enabled = !busy && selected.isNotEmpty(), onClick = {
                    val ids = selected.keys.toList()
                    perform { actions.restore(ids) }
                }) { Text(pluralStringResource(R.plurals.hidden_restore_selected, selected.size, selected.size)) }
            } else {
                TextButton(enabled = !busy && total > 0, onClick = { confirmAll = true }) {
                    Text(stringResource(R.string.hidden_track_restore_all))
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (selectionLimit) Text(stringResource(R.string.hidden_restore_limit, MaxSelection), Modifier.padding(horizontal = 24.dp))
        if (failed) Text(stringResource(R.string.hidden_restore_failed), Modifier.padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.error)
        restored?.let { count ->
            Text(pluralStringResource(R.plurals.hidden_restore_done, count, count),
                Modifier.padding(horizontal = 24.dp).semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (pages.loadState.refresh is LoadState.Error) {
            TextButton(enabled = !busy, onClick = pages::retry) { Text(stringResource(R.string.hidden_track_retry)) }
        } else if (pages.itemCount == 0 && pages.loadState.refresh !is LoadState.Loading) {
            Text(stringResource(R.string.hidden_track_empty), Modifier.padding(24.dp))
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 480.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)) {
            items(pages.itemCount, key = pages.itemKey { it.trackId }) { index ->
                pages[index]?.let { track ->
                    val checked = selected.containsKey(track.trackId)
                    val selectionModifier = if (!selecting) Modifier else Modifier.toggleable(
                        checked, enabled = !busy, role = Role.Checkbox,
                        onValueChange = { value ->
                            if (!value) { selected.remove(track.trackId); selectionLimit = false }
                            else if (selected.size < MaxSelection) selected[track.trackId] = true
                            else selectionLimit = true
                        })
                    Row(Modifier.fillMaxWidth().then(selectionModifier).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (selecting) Checkbox(checked = checked, onCheckedChange = null, enabled = !busy)
                        Column(Modifier.weight(1f)) {
                            Text(displayTrack(track.title), style = MaterialTheme.typography.titleSmall)
                            Text(displayArtist(track.artist), style = MaterialTheme.typography.bodySmall)
                        }
                        if (!selecting) TextButton(enabled = !busy, onClick = {
                            perform { actions.restore(listOf(track.trackId)) }
                        }) { Text(stringResource(R.string.hidden_track_restore)) }
                    }
                }
            }
            if (pages.loadState.append is LoadState.Error) item {
                TextButton(enabled = !busy, onClick = pages::retry) { Text(stringResource(R.string.hidden_track_retry)) }
            }
        }
    }
    if (confirmAll) AlertDialog(
        onDismissRequest = { confirmAll = false },
        title = { Text(stringResource(R.string.hidden_track_restore_all)) },
        text = { Text(pluralStringResource(R.plurals.hidden_restore_all_confirm, total, total)) },
        confirmButton = { TextButton(enabled = !busy && total > 0, onClick = {
            confirmAll = false; perform(actions.restoreAll)
        }) { Text(stringResource(R.string.hidden_track_restore_all)) } },
        dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(stringResource(R.string.hidden_track_cancel)) } },
    )
}
