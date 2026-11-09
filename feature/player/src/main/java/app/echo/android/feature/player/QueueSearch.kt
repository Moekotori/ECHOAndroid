package app.echo.android.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoSearchField
import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.playback.PlaybackQueueState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal data class QueueSearchHit(val entryId: String?, val index: Int, val position: Int, val track: EchoTrackRef,
    val snapshot: List<EchoTrackRef>)

internal fun searchQueue(queue: PlaybackQueueState, query: String): List<QueueSearchHit> {
    val term = query.trim()
    if (term.isEmpty()) return emptyList()
    val hits = ArrayList<QueueSearchHit>()
    for ((position, index) in queue.playOrder.withIndex()) {
        val track = queue.items.getOrNull(index) ?: continue
        if (track.title.contains(term, true) || track.artist?.contains(term, true) == true || track.album?.contains(term, true) == true)
            hits += QueueSearchHit(track.queueContext?.entryId, index, position + 1, track, queue.items)
        if (hits.size == 200) break
    }
    return hits
}

internal fun queueSearchTarget(hit: QueueSearchHit, queue: PlaybackQueueState): Int? =
    if (hit.entryId != null) queue.items.indexOfFirst { it.queueContext?.entryId == hit.entryId }.takeIf { it >= 0 }
    else hit.index.takeIf { queue.items == hit.snapshot && queue.items.getOrNull(it) == hit.track }

@Composable
internal fun QueueSearchDialog(queue: PlaybackQueueState, onDismiss: () -> Unit, onPlayItem: (Int) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    // The dialog owns text input so the IME does not resize the player window.
    Dialog(onDismissRequest = onDismiss) {
        val focusRequester = remember { FocusRequester() }
        val windowInfo = LocalWindowInfo.current
        val keyboard = LocalSoftwareKeyboardController.current
        val title = stringResource(R.string.queue_search)
        LaunchedEffect(windowInfo) {
            snapshotFlow { windowInfo.isWindowFocused }.first { it }
            focusRequester.requestFocus()
            keyboard?.show()
        }
        Surface(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = queueSurfaceColor(),
        ) {
            Column(Modifier.heightIn(max = 420.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = onDismiss) {
                        EchoIcon(Icons.Rounded.Close, stringResource(R.string.track_tools_cancel), Modifier.size(22.dp))
                    }
                }
                EchoSearchField(
                    query = query,
                    onQuery = { query = it },
                    placeholder = title,
                    clearDescription = stringResource(R.string.queue_search_clear),
                    modifier = Modifier.focusRequester(focusRequester),
                )
                if (query.isNotBlank()) QueueSearchResults(queue, query) { index ->
                    onDismiss()
                    onPlayItem(index)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.QueueSearchResults(queue: PlaybackQueueState, query: String, onPlayItem: (Int) -> Unit) {
    var hits by remember(queue.items, queue.playOrder, query) { mutableStateOf<List<QueueSearchHit>?>(null) }
    val latest by rememberUpdatedState(queue)
    LaunchedEffect(queue.items, queue.playOrder, query) {
        delay(180)
        hits = withContext(Dispatchers.Default) { searchQueue(queue, query) }
    }
    val results = hits
    if (results == null) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        return
    }
    LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
        if (results.isEmpty()) item { Text(stringResource(R.string.queue_search_empty), Modifier.padding(vertical = 16.dp)) }
        items(results, key = { it.entryId ?: "${it.track.id}:${it.index}" }) { hit ->
            Column(Modifier.fillMaxWidth().clickable { queueSearchTarget(hit, latest)?.let(onPlayItem) }
                .heightIn(min = 64.dp).padding(vertical = 10.dp)) {
                Text(hit.track.title, style = MaterialTheme.typography.titleSmall)
                Text(listOfNotNull(hit.track.artist, hit.track.album).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.queue_search_position, hit.position), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
