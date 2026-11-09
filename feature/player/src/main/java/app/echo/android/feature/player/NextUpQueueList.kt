package app.echo.android.feature.player

import app.echo.android.design.detectEchoTargetDrag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.PlaybackQueueState
import app.echo.android.design.rememberEchoHapticPerformer
import app.echo.android.design.EchoMotion
import app.echo.android.design.EchoDragAutoScroll
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import kotlin.math.abs
import kotlinx.coroutines.delay

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
    var dragSource by remember(queue) { mutableStateOf<QueueDragTarget?>(null) }
    var dropTarget by remember(queue) { mutableStateOf<QueueDragTarget?>(null) }
    var released by remember(queue) { mutableStateOf(false) }
    var awaitingQueue by remember(queue) { mutableStateOf(false) }
    var commitDrop by remember(queue) { mutableStateOf(false) }
    var destinationTop by remember(queue) { mutableFloatStateOf(0f) }
    var releaseTop by remember(queue) { mutableFloatStateOf(0f) }
    var settlement by remember(queue) { mutableIntStateOf(0) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var grabOffsetY by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableIntStateOf(0) }
    val animateItems = !lightweight && dragSource == null
    val move by rememberUpdatedState(onMove)
    val menuWidth = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }

    fun finishDrag(commit: Boolean) {
        val source = dragSource ?: return
        val target = if (commit) dropTarget ?: source else source
        if (!commit) dropTarget = source
        releaseTop = if (released) destinationTop else pointerY - grabOffsetY
        destinationTop = state.layoutInfo.visibleItemsInfo.firstOrNull {
            dragTargets[it.key]?.queueIndex == target.queueIndex
        }?.offset?.toFloat() ?: (pointerY - grabOffsetY)
        commitDrop = commit && source.queueIndex != target.queueIndex
        settlement++
        released = true
        haptics.endSeek(committed = commitDrop)
    }

    // A rejected or delayed edit returns through the same landing animation instead of snapping.
    LaunchedEffect(awaitingQueue, queue) {
        if (awaitingQueue) {
            delay(1_000)
            awaitingQueue = false
            finishDrag(commit = false)
        }
    }

    fun updateDropTarget() {
        if (released) return
        val group = dragSource?.group ?: return
        var next: QueueDragTarget? = null
        var nearest = Float.MAX_VALUE
        val center = queueDragCenter(pointerY, grabOffsetY, rowHeight)
        for (info in state.layoutInfo.visibleItemsInfo) {
            val candidate = dragTargets[info.key]?.takeIf { it.group == group } ?: continue
            val distance = abs(info.offset + info.size / 2f - center)
            if (distance < nearest) { nearest = distance; next = candidate }
        }
        if (next != null && next != dropTarget) haptics.seekStep(next.row)
        dropTarget = next
    }

    EchoDragAutoScroll(state, dragging = dragSource != null && !released, pointerY = { pointerY }) {
        updateDropTarget()
    }
    LaunchedEffect(queue.currentItem?.queueContext?.entryId ?: queue.currentIndex) { state.scrollToItem(0) }
    Box(Modifier.fillMaxWidth().clipToBounds()) {
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxWidth().pointerInput(state, queue, dragTargets, menuWidth) {
            detectEchoTargetDrag(
                longPress = true,
                targetAt = { offset ->
                    if (dragSource != null) return@detectEchoTargetDrag null
                    if (!queueDragHitAllowed(offset.x, size.width.toFloat(), menuWidth)) return@detectEchoTargetDrag null
                    val info = state.layoutInfo.visibleItemsInfo.firstOrNull {
                        offset.y >= it.offset && offset.y < it.offset + it.size
                    }
                    info?.let { dragTargets[it.key]?.let { target -> target to it } }
                },
                onStart = { (target, info), offset ->
                    val current = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == info.key }
                        ?: return@detectEchoTargetDrag
                    dragSource = target
                    dropTarget = target
                    released = false
                    awaitingQueue = false
                    commitDrop = false
                    pointerY = offset.y
                    grabOffsetY = offset.y - current.offset
                    rowHeight = current.size
                    haptics.grab()
                },
                onDrag = { change, _ ->
                    if (dragSource != null && !released) {
                        change.consume()
                        pointerY = change.position.y
                        updateDropTarget()
                    }
                },
                onStop = { finishDrag(commit = true) },
                onCancel = { finishDrag(commit = false) },
            )
        },
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        userScrollEnabled = dragSource == null,
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
                    modifier = queueItemMotion(animateItems).queueDragPreview(
                        row, 0, index, dragSource, dropTarget, rowHeight, lightweight),
                    interactive = dragSource == null)
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
                    modifier = queueItemMotion(animateItems).queueDragPreview(
                        row, 1, index, dragSource, dropTarget, rowHeight, lightweight),
                    interactive = dragSource == null)
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
            QueueDragOverlay(
                track = track,
                source = source,
                rowHeight = rowHeight,
                top = { if (released) releaseTop else pointerY - grabOffsetY },
                released = released,
                settlement = settlement,
                destinationTop = destinationTop,
                lightweight = lightweight,
                onSettled = {
                    if (awaitingQueue) return@QueueDragOverlay
                    val target = dropTarget
                    if (commitDrop && target != null) {
                        awaitingQueue = true
                        move(source.queueIndex, target.queueIndex)
                    } else {
                        dragSource = null
                        dropTarget = null
                        released = false
                    }
                },
            )
        }
    }
    }
}

@Composable
private fun Modifier.queueDragPreview(
    row: Int, group: Int, queueIndex: Int,
    source: QueueDragTarget?, target: QueueDragTarget?, rowHeight: Int, lightweight: Boolean,
): Modifier {
    val displacement by animateFloatAsState(
        targetValue = queueDragDisplacement(row, group, source, target, rowHeight),
        animationSpec = if (source == null) snap() else if (lightweight) tween(90) else EchoMotion.silkFloat(240),
        label = "queue-drag-gap",
    )
    return graphicsLayer {
        translationY = displacement
        alpha = if (source?.queueIndex == queueIndex) 0f else 1f
    }.then(if (source?.queueIndex == queueIndex) Modifier.clearAndSetSemantics {} else Modifier)
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
