package app.echo.android.feature.player

import androidx.compose.foundation.background
import app.echo.android.design.detectEchoTargetDrag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.PlaybackQueueState
import app.echo.android.design.rememberEchoHapticPerformer
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private data class QueueDragTarget(val queueIndex: Int, val row: Int, val lastRow: Int, val group: Int)

@Composable
internal fun NextUpQueueList(
    queue: PlaybackQueueState,
    playing: Boolean,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClearNextUp: () -> Unit,
) {
    val nextUp = remember(queue) { queue.nextUpIndices }
    val continuation = remember(queue) { queue.continuationIndices }
    val previous = remember(queue, continuation) {
        val upcoming = continuation.toHashSet()
        queue.playOrder.filter { it != queue.currentIndex && it !in upcoming && queue.items[it].queueContext?.nextUp != true }
    }
    var showPrevious by rememberSaveable { mutableStateOf(false) }
    val state = rememberLazyListState()
    val haptics by rememberUpdatedState(rememberEchoHapticPerformer())
    val dragTargets = remember(queue, nextUp, continuation) {
        buildMap<Any, QueueDragTarget> {
            nextUp.forEachIndexed { row, index ->
                put(queue.items[index].queueContext?.entryId ?: "next-$index", QueueDragTarget(index, row, nextUp.lastIndex, 0))
            }
            continuation.forEachIndexed { row, index ->
                put(queue.items[index].queueContext?.entryId ?: "base-$index", QueueDragTarget(index, row, continuation.lastIndex, 1))
            }
        }
    }
    var dragSource by remember { mutableStateOf<QueueDragTarget?>(null) }
    var dropTarget by remember { mutableStateOf<QueueDragTarget?>(null) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var grabOffsetY by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val edgeSize = with(density) { 48.dp.toPx() }
    val edgeStep = with(density) { 12.dp.toPx() }

    fun updateDropTarget() {
        val group = dragSource?.group ?: return
        val next = state.layoutInfo.visibleItemsInfo.mapNotNull { info ->
            dragTargets[info.key]?.takeIf { it.group == group }?.let { it to info }
        }.minByOrNull { (_, info) -> abs(info.offset + info.size / 2f - pointerY) }?.first
        if (next != null && next != dropTarget) haptics.seekStep(next.row)
        dropTarget = next
    }

    LaunchedEffect(dragSource) {
        while (dragSource != null) {
            delay(16)
            val layout = state.layoutInfo
            val direction = when {
                pointerY < layout.viewportStartOffset + edgeSize -> -1f
                pointerY > layout.viewportEndOffset - edgeSize -> 1f
                else -> 0f
            }
            if (direction != 0f) {
                state.scrollBy(direction * edgeStep)
                updateDropTarget()
            }
        }
    }
    LaunchedEffect(queue.currentItem?.queueContext?.entryId ?: queue.currentIndex) { state.scrollToItem(0) }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().pointerInput(state, dragTargets) {
            detectEchoTargetDrag(
                longPress = true,
                targetAt = { offset ->
                    val info = state.layoutInfo.visibleItemsInfo.firstOrNull {
                        offset.y >= it.offset && offset.y < it.offset + it.size
                    }
                    info?.let { dragTargets[it.key]?.let { target -> target to it } }
                },
                onStart = { (target, info), offset ->
                    dragSource = target
                    dropTarget = target
                    pointerY = offset.y
                    grabOffsetY = offset.y - info.offset
                    rowHeight = info.size
                    haptics.grab()
                    haptics.seekStep(target.row)
                },
                onDrag = { change, amount ->
                    if (dragSource != null) {
                        change.consume()
                        pointerY += amount.y
                        updateDropTarget()
                    }
                },
                onStop = {
                    val from = dragSource?.queueIndex
                    val to = dropTarget?.queueIndex
                    dragSource = null
                    dropTarget = null
                    if (from != null && to != null && from != to) {
                        onMove(from, to)
                        haptics.endSeek(committed = true)
                    } else haptics.endSeek(committed = false)
                },
                onCancel = {
                    haptics.endSeek(committed = false)
                    dragSource = null
                    dropTarget = null
                },
            )
        },
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        queue.currentItem?.let { track ->
            item(key = "now-label") { QueueSectionTitle(stringResource(R.string.feature_player_now_playing_214a7c)) }
            item(key = "now") {
                QueueTrackRow(track, 0, 0, true, { onPlay(queue.currentIndex) }, {}, {}, {}, playing = playing)
            }
        }
        if (nextUp.isNotEmpty()) {
            item(key = "next-up-label") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.queue_next_up), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = onClearNextUp) { Text(stringResource(R.string.queue_clear_next_up)) }
                }
            }
            itemsIndexed(nextUp, key = { _, index -> queue.items[index].queueContext?.entryId ?: "next-$index" }) { row, index ->
                QueueTrackRow(queue.items[index], row, nextUp.lastIndex, false,
                    { onPlay(index) }, { onRemove(index) },
                    { nextUp.getOrNull(row - 1)?.let { onMove(index, it) } },
                    { nextUp.getOrNull(row + 1)?.let { onMove(index, it) } },
                    modifier = Modifier.graphicsLayer { alpha = if (dragSource?.queueIndex == index) 0f else 1f },
                    dropTarget = dropTarget?.queueIndex == index && dragSource?.queueIndex != index)
            }
        }
        if (continuation.isNotEmpty()) {
            item(key = "continue-label") {
                QueueSectionTitle(queue.source?.let { stringResource(R.string.queue_continue_from, it) }
                    ?: stringResource(R.string.queue_continue))
            }
            itemsIndexed(continuation, key = { _, index -> queue.items[index].queueContext?.entryId ?: "base-$index" }) { row, index ->
                QueueTrackRow(queue.items[index], row, continuation.lastIndex, false,
                    { onPlay(index) }, { onRemove(index) },
                    { continuation.getOrNull(row - 1)?.let { onMove(index, it) } },
                    { continuation.getOrNull(row + 1)?.let { onMove(index, it) } },
                    modifier = Modifier.graphicsLayer { alpha = if (dragSource?.queueIndex == index) 0f else 1f },
                    dropTarget = dropTarget?.queueIndex == index && dragSource?.queueIndex != index)
            }
        }
        if (previous.isNotEmpty()) {
            item(key = "previous-label") {
                TextButton(onClick = { showPrevious = !showPrevious }) {
                    Text(stringResource(if (showPrevious) R.string.queue_hide_previous else R.string.queue_show_previous))
                }
            }
            if (showPrevious) {
                itemsIndexed(previous, key = { _, index -> queue.items[index].queueContext?.entryId ?: "previous-$index" }) { _, index ->
                    QueueTrackRow(queue.items[index], 0, 0, false, { onPlay(index) }, { onRemove(index) }, {}, {}, reorderable = false)
                }
            }
        }
    }
    dragSource?.let { source ->
        queue.items.getOrNull(source.queueIndex)?.let { track ->
            QueueTrackRow(track, source.row, source.lastRow, false, {}, {}, {}, {},
                modifier = Modifier
                    .offset { IntOffset(0, (pointerY - grabOffsetY).roundToInt()) }
                    .height(with(density) { rowHeight.toDp() })
                    .background(queueSurfaceColor()),
                interactive = false)
        }
    }
    }
}

@Composable
private fun QueueSectionTitle(title: String) {
    Text(title, Modifier.padding(top = 16.dp, bottom = 6.dp), color = app.echo.android.design.echoIconColor().copy(alpha = 0.65f), style = MaterialTheme.typography.labelLarge,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}
