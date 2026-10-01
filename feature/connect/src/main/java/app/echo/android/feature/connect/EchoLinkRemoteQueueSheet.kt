package app.echo.android.feature.connect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.echo.android.design.EchoIcon
import app.echo.android.design.detectEchoTargetDrag
import app.echo.android.model.connect.EchoRemotePlaybackSnapshot
import app.echo.android.model.connect.EchoRemoteQueueState
import app.echo.android.model.connect.EchoRemoteTrack
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoLinkRemoteQueueSheet(
    currentTrackId: String?,
    remoteError: String?,
    queue: EchoRemoteQueueState,
    connected: Boolean,
    pcTitle: String,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onLoadPrevious: () -> Unit,
    onMove: (String, Int) -> Unit,
    onVisibleAnchor: (Int) -> Unit,
    onPlayTrack: (EchoRemoteTrack) -> Unit,
    onDismiss: () -> Unit,
) {
    val list = rememberLazyListState()
    val latestQueue by rememberUpdatedState(queue)
    val move by rememberUpdatedState(onMove)
    val loadMore by rememberUpdatedState(onLoadMore)
    val loadPrevious by rememberUpdatedState(onLoadPrevious)
    val reportAnchor by rememberUpdatedState(onVisibleAnchor)
    LaunchedEffect(list) {
        snapshotFlow { latestQueue.offset + list.firstVisibleItemIndex }.distinctUntilChanged().collect { reportAnchor(it) }
    }
    var dragged by remember { mutableStateOf<EchoRemoteTrack?>(null) }
    var displacement by remember { mutableFloatStateOf(0f) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var destination by remember { mutableIntStateOf(-1) }
    val handleWidth = with(LocalDensity.current) { 48.dp.toPx() }
    val edgeStep = with(LocalDensity.current) { 10.dp.toPx() }
    val canMove = connected && !queue.unsupported && !queue.isMoving && !queue.isLoading
    LaunchedEffect(dragged?.queueId) {
        if (dragged == null) return@LaunchedEffect
        while (isActive) {
            val layout = list.layoutInfo
            val step = when {
                pointerY < layout.viewportStartOffset + handleWidth -> -edgeStep
                pointerY > layout.viewportEndOffset - handleWidth -> edgeStep
                else -> 0f
            }
            if (step != 0f) {
                list.scrollBy(step)
                list.layoutInfo.visibleItemsInfo.minByOrNull { kotlin.math.abs(pointerY - it.offset - it.size / 2f) }
                    ?.let { latestQueue.items.getOrNull(it.index)?.queueIndex }?.let { destination = it }
            }
            delay(32)
        }
    }
    LaunchedEffect(list, queue.items.size, queue.offset, queue.totalCount, queue.isLoadingMore, queue.unsupported) {
        if (queue.unsupported || queue.isLoadingMore || queue.items.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            when {
                list.firstVisibleItemIndex < 3 && queue.offset > 0 -> -1
                (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= queue.items.size - 8 &&
                    queue.offset + queue.items.size < queue.totalCount -> 1
                else -> 0
            }
        }.distinctUntilChanged().collect { if (it < 0) loadPrevious() else if (it > 0) loadMore() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.remote_queue_count, queue.totalCount), style = MaterialTheme.typography.headlineSmall)
                    Text(pcTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onDismiss) { EchoIcon(Icons.Rounded.Close, stringResource(R.string.remote_close)) }
            }
            Text(stringResource(if (queue.unsupported) R.string.remote_queue_update_pc else R.string.remote_queue_drag_hint),
                Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (queue.isLoading || queue.isLoadingMore || queue.isMoving) LinearProgressIndicator(Modifier.fillMaxWidth())
            (queue.error ?: remoteError)?.let { error ->
                Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onRefresh, enabled = connected && !queue.isMoving) { Text(stringResource(R.string.feature_connect_refresh_828c69)) }
                }
            }
            if (queue.items.isEmpty() && !queue.isLoading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.remote_empty_queue), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(state = list, contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth().pointerInput(list, canMove) {
                        if (!canMove) return@pointerInput
                        detectEchoTargetDrag(longPress = true,
                            targetAt = { point ->
                                if (point.x < size.width - handleWidth) null else {
                                    val index = list.layoutInfo.visibleItemsInfo.firstOrNull { point.y >= it.offset && point.y < it.offset + it.size }?.index
                                    index?.let { latestQueue.items.getOrNull(it) }?.takeIf { it.queueId != null }
                                }
                            },
                            onStart = { item, point -> dragged = item; displacement = 0f; pointerY = point.y; destination = item.queueIndex ?: -1 },
                            onDrag = { _, delta ->
                                displacement += delta.y; pointerY += delta.y
                                val target = list.layoutInfo.visibleItemsInfo.minByOrNull { kotlin.math.abs(pointerY - it.offset - it.size / 2f) }
                                target?.let { latestQueue.items.getOrNull(it.index)?.queueIndex }?.let { destination = it }
                            },
                            onStop = {
                                val source = dragged
                                val sourceId = source?.queueId
                                dragged = null; displacement = 0f
                                if (sourceId != null && destination >= 0 && destination != source.queueIndex) move(sourceId, destination)
                            },
                            onCancel = { dragged = null; displacement = 0f },
                        )
                    }) {
                    itemsIndexed(queue.items, key = { index, track -> track.queueId ?: "legacy-$index" }) { _, track ->
                        val queueId = track.queueId
                        val queueIndex = track.queueIndex
                        var menu by remember(track.queueId) { mutableStateOf(false) }
                        val dragging = track.queueId != null && dragged?.queueId == track.queueId
                        Row(Modifier.fillMaxWidth().zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer { translationY = if (dragging) displacement else 0f }, verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                RemoteMusicRow(track, current = if (queue.currentQueueId != null) track.queueId == queue.currentQueueId
                                    else track.id == currentTrackId, enabled = connected && !queue.isMoving && !queue.isLoading,
                                    onClick = { onPlayTrack(track) })
                            }
                            if (!queue.unsupported && queueId != null && queueIndex != null) {
                                Box {
                                    IconButton({ menu = true }, enabled = canMove) { EchoIcon(Icons.Rounded.MoreVert, stringResource(R.string.remote_queue_move)) }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.remote_queue_move_up)) }, enabled = queueIndex > queue.offset,
                                            onClick = { menu = false; onMove(queueId, queueIndex - 1) })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.remote_queue_move_down)) }, enabled = queueIndex < queue.offset + queue.items.lastIndex,
                                            onClick = { menu = false; onMove(queueId, queueIndex + 1) })
                                    }
                                }
                                EchoIcon(Icons.Rounded.DragHandle, stringResource(R.string.remote_queue_move), Modifier.size(48.dp).padding(12.dp))
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}
