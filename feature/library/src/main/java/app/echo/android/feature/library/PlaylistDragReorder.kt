package app.echo.android.feature.library

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.rememberEchoHapticPerformer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

internal class PlaylistDragState(val list: LazyListState) {
    var source by mutableStateOf<String?>(null)
    var target by mutableStateOf<String?>(null)
    var y by mutableFloatStateOf(0f)
    var listOrigin = Offset.Zero
    // Only composed handles register bounds; scrolling removes recycled rows.
    val handles = mutableMapOf<String, Rect>()
    fun reset() { source = null; target = null }
    fun updateTarget(loaded: Set<String>): Boolean {
        var distance = Float.MAX_VALUE
        var nearest = target
        for (item in list.layoutInfo.visibleItemsInfo) {
            val id = item.key as? String ?: continue
            if (id !in loaded) continue
            val nextDistance = abs(item.offset + item.size / 2f - y)
            if (nextDistance < distance) { distance = nextDistance; nearest = id }
        }
        val changed = nearest != target
        target = nearest
        return changed
    }
}

@Composable
internal fun rememberPlaylistDragState(list: LazyListState, enabled: Boolean, loaded: Set<String>): PlaylistDragState {
    val drag = remember(list) { PlaylistDragState(list) }
    val loadedNow by rememberUpdatedState(loaded)
    val haptics = rememberEchoHapticPerformer()
    val edge = with(LocalDensity.current) { 48.dp.toPx() }
    val step = with(LocalDensity.current) { 12.dp.toPx() }
    DisposableEffect(drag) { onDispose { drag.reset(); drag.handles.clear() } }
    LaunchedEffect(enabled) { if (!enabled) drag.reset() }
    LaunchedEffect(drag.source) {
        while (drag.source != null) {
            delay(16)
            val layout = list.layoutInfo
            // The bottom content padding reserves space for the floating player/dock.
            val visibleBottom = layout.viewportEndOffset - minOf(layout.afterContentPadding,
                (layout.viewportEndOffset - layout.viewportStartOffset) / 2)
            val direction = when {
                drag.y < layout.viewportStartOffset + edge -> -1
                drag.y > visibleBottom - edge -> 1
                else -> 0
            }
            if (direction != 0) {
                list.scrollBy(direction * step)
                if (drag.updateTarget(loadedNow)) haptics.tick()
            }
        }
    }
    return drag
}

/** The list owns the gesture, so recycling the source row or its Paging page cannot cancel it. */
@Composable
internal fun Modifier.playlistDragReorder(drag: PlaylistDragState, enabled: Boolean, loaded: Set<String>,
    onMove: (String, String) -> Unit): Modifier {
    val loadedNow by rememberUpdatedState(loaded)
    val move by rememberUpdatedState(onMove)
    val haptics = rememberEchoHapticPerformer()
    val scope = rememberCoroutineScope()
    return onGloballyPositioned { drag.listOrigin = it.positionInRoot() }.pointerInput(drag, enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val rootPoint = down.position + drag.listOrigin
            val id = drag.handles.entries.firstOrNull { (id, rect) -> id in loadedNow && rect.contains(rootPoint) }?.key
                ?: return@awaitEachGesture
            // Only the explicit handle takes the initial pass; the rest of the row keeps normal scrolling/clicks.
            down.consume()
            scope.launch { drag.list.stopScroll() }
            drag.source = id; drag.target = id; drag.y = down.position.y
            haptics.grab()
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    drag.y = change.position.y
                    if (drag.updateTarget(loadedNow)) haptics.tick()
                    if (!change.pressed) {
                        val target = drag.target
                        drag.reset()
                        if (target != null && target != id) { haptics.drop(); move(id, target) }
                        break
                    }
                }
            } finally { drag.reset() }
        }
    }
}

@Composable
internal fun PlaylistDragHandle(id: String, drag: PlaylistDragState) {
    DisposableEffect(drag, id) { onDispose { drag.handles.remove(id) } }
    Box(Modifier.size(48.dp).onGloballyPositioned { drag.handles[id] = it.boundsInRoot() }, contentAlignment = Alignment.Center) {
        EchoIcon(Icons.Rounded.DragHandle, stringResource(R.string.playlist_drag_handle), Modifier.size(24.dp))
    }
}
