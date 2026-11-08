package app.echo.android.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.playback.PlaybackQueueState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
internal fun QueueSearchField(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(query, onQuery, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        singleLine = true, label = { Text(stringResource(R.string.queue_search)) },
        trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { onQuery("") }) { Text(stringResource(R.string.queue_search_clear)) } })
}

@Composable
internal fun ColumnScope.QueueSearchResults(queue: PlaybackQueueState, query: String, onPlayItem: (Int) -> Unit) {
    var hits by remember { mutableStateOf<List<QueueSearchHit>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val latest by rememberUpdatedState(queue)
    LaunchedEffect(queue.items, queue.playOrder, query) {
        hits = emptyList(); loading = true
        delay(180)
        hits = withContext(Dispatchers.Default) { searchQueue(queue, query) }
        loading = false
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
        if (!loading && hits.isEmpty()) item { Text(stringResource(R.string.queue_search_empty), Modifier.padding(vertical = 16.dp)) }
        items(hits, key = { it.entryId ?: "${it.track.id}:${it.index}" }) { hit ->
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
