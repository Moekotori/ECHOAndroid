package app.echo.android.feature.player

import androidx.compose.foundation.background
import app.echo.android.design.detectEchoTargetDrag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private data class QueueDragTarget(val queueIndex: Int, val row: Int, val lastRow: Int, val group: Int)

internal data class QueueSections(val nextUp: List<Int>, val continuation: List<Int>, val previous: List<Int>)

internal fun queueSections(queue: PlaybackQueueState): QueueSections {
    val nextUp = queue.nextUpIndices
    val continuation = queue.continuationIndices
    val upcoming = continuation.toHashSet()
    val previous = queue.playOrder.filter {
        it != queue.currentIndex && it !in upcoming && queue.items[it].queueContext?.nextUp != true
    }
    return QueueSections(nextUp, continuation, previous)
}

@Composable
internal fun NextUpQueueList(
    queue: PlaybackQueueState,
    playing: Boolean,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    sections: QueueSections,
    state: androidx.compose.foundation.lazy.LazyListState,
    showPrevious: Boolean,
    onTogglePrevious: () -> Unit,
) {
    val (nextUp, continuation, previous) = sections
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val haptics by rememberUpdatedState(rememberEchoHapticPerformer())
    val dragTargets = remember(queue, nextUp, continuation) {
        buildMap<Any, QueueDragTarget> {
            nextUp.forEachIndexed { row, index ->
                put(queue.items[index].queueContext?.entryId ?: "queue-$index", QueueDragTarget(index, row, nextUp.lastIndex, 0))
            }
            continuation.forEachIndexed { row, index ->
                put(queue.items[index].queueContext?.entryId ?: "queue-$index", QueueDragTarget(index, row, continuation.lastIndex, 1))
            }
        }
    }
    var dragSource by remember { mutableStateOf<QueueDragTarget?>(null) }
    var dropTarget by remember { mutableStateOf<QueueDragTarget?>(null) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var grabOffsetY by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableIntStateOf(0) }
    val animateItems = !lightweight && dragSource == null
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
            item(key = "now-label") {
                QueueSectionTitle(stringResource(R.string.feature_player_now_playing_214a7c),
                    modifier = queueItemMotion(animateItems))
            }
            item(key = "now") {
                QueueTrackRow(track, 0, 0, true, { onPlay(queue.currentIndex) }, {}, {}, {}, playing = playing,
                    modifier = queueItemMotion(animateItems))
            }
        }
        if (nextUp.isNotEmpty()) {
            item(key = "next-up-label") {
                QueueSectionTitle(stringResource(R.string.queue_next_up), nextUp.size,
                    modifier = queueItemMotion(animateItems))
            }
            itemsIndexed(nextUp, key = { _, index -> queue.items[index].queueContext?.entryId ?: "queue-$index" }) { row, index ->
                QueueTrackRow(queue.items[index], row, nextUp.lastIndex, false,
                    { onPlay(index) }, { onRemove(index) },
                    { nextUp.getOrNull(row - 1)?.let { onMove(index, it) } },
                    { nextUp.getOrNull(row + 1)?.let { onMove(index, it) } },
                    modifier = queueItemMotion(animateItems)
                        .graphicsLayer { alpha = if (dragSource?.queueIndex == index) 0f else 1f },
                    dropTarget = dropTarget?.queueIndex == index && dragSource?.queueIndex != index)
            }
        }
        if (continuation.isNotEmpty()) {
            item(key = "continue-label") {
                QueueSectionTitle(queue.source?.let { stringResource(R.string.queue_continue_from, it) }
                    ?: stringResource(R.string.queue_continue), continuation.size,
                    modifier = queueItemMotion(animateItems))
            }
            itemsIndexed(continuation, key = { _, index -> queue.items[index].queueContext?.entryId ?: "queue-$index" }) { row, index ->
                QueueTrackRow(queue.items[index], row, continuation.lastIndex, false,
                    { onPlay(index) }, { onRemove(index) },
                    { continuation.getOrNull(row - 1)?.let { onMove(index, it) } },
                    { continuation.getOrNull(row + 1)?.let { onMove(index, it) } },
                    modifier = queueItemMotion(animateItems)
                        .graphicsLayer { alpha = if (dragSource?.queueIndex == index) 0f else 1f },
                    dropTarget = dropTarget?.queueIndex == index && dragSource?.queueIndex != index)
            }
        }
        if (queue.currentItem != null && nextUp.isEmpty() && continuation.isEmpty()) {
            item(key = "queue-end") {
                Text(
                    stringResource(R.string.queue_end_reached),
                    queueItemMotion(animateItems).padding(top = 16.dp, start = 10.dp, bottom = 4.dp),
                    color = app.echo.android.design.echoIconColor().copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (previous.isNotEmpty()) {
            item(key = "previous-label") {
                TextButton(onClick = onTogglePrevious, modifier = queueItemMotion(animateItems)) {
                    Text(stringResource(if (showPrevious) R.string.queue_hide_previous else R.string.queue_show_previous),
                        color = app.echo.android.design.echoIconColor().copy(alpha = 0.65f))
                }
            }
            if (showPrevious) {
                itemsIndexed(previous, key = { _, index -> queue.items[index].queueContext?.entryId ?: "queue-$index" }) { _, index ->
                    QueueTrackRow(queue.items[index], 0, 0, false, { onPlay(index) }, { onRemove(index) }, {}, {},
                        reorderable = false, modifier = queueItemMotion(animateItems))
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

private fun LazyItemScope.queueItemMotion(enabled: Boolean): Modifier = if (enabled) {
    Modifier.animateItem(
        fadeInSpec = tween(240, easing = EchoMotion.Silk),
        placementSpec = EchoMotion.silkOffset(420),
        fadeOutSpec = tween(180, easing = EchoMotion.SilkExit),
    )
} else Modifier

@Composable
private fun QueueSectionTitle(title: String, count: Int? = null, modifier: Modifier = Modifier) {
    val ink = app.echo.android.design.echoIconColor()
    Row(modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = ink.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (count != null) Text(count.toString(), color = ink.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
    }
}
