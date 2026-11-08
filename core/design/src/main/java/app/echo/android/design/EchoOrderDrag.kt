package app.echo.android.design

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import kotlinx.coroutines.launch
import kotlin.math.abs

class EchoOrderDragState internal constructor(internal val list: LazyListState) {
    var source by mutableStateOf<String?>(null)
    var target by mutableStateOf<String?>(null)
    var y by mutableFloatStateOf(0f)
    internal var grabOffset = 0f
    internal var listOrigin = Offset.Zero
    // Only composed handles register bounds; scrolling removes recycled rows.
    internal val handles = mutableMapOf<String, Rect>()
    fun reset() { source = null; target = null }
    internal fun updateTarget(loaded: Set<String>): Boolean {
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
fun rememberEchoOrderDragState(list: LazyListState, enabled: Boolean, loaded: Set<String>): EchoOrderDragState {
    val drag = remember(list) { EchoOrderDragState(list) }
    val loadedNow by rememberUpdatedState(loaded)
    val haptics = rememberEchoHapticPerformer()
    DisposableEffect(drag) { onDispose { drag.reset(); drag.handles.clear() } }
    LaunchedEffect(enabled) { if (!enabled) drag.reset() }
    EchoDragAutoScroll(list, dragging = drag.source != null, pointerY = { drag.y }) {
        if (drag.updateTarget(loadedNow)) haptics.tick()
    }
    return drag
}

/** The list owns the gesture, so recycling the source row cannot cancel it. */
@Composable
fun Modifier.echoOrderDrag(drag: EchoOrderDragState, enabled: Boolean, loaded: Set<String>,
    onMove: (String, String) -> Unit): Modifier {
    val loadedNow by rememberUpdatedState(loaded)
    val move by rememberUpdatedState(onMove)
    val haptics = rememberEchoHapticPerformer()
    val scope = rememberCoroutineScope()
    return onGloballyPositioned { drag.listOrigin = it.positionInRoot() }.pointerInput(drag, enabled, haptics) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            if (currentEvent.changes.size != 1) return@awaitEachGesture
            val rootPoint = down.position + drag.listOrigin
            val id = drag.handles.entries.firstOrNull { (id, rect) -> id in loadedNow && rect.contains(rootPoint) }?.key
                ?: return@awaitEachGesture
            // Only the explicit handle takes the initial pass; the rest of the row keeps normal scrolling/clicks.
            down.consume()
            val stopScroll = scope.launch { drag.list.stopScroll() }
            drag.grabOffset = down.position.y - (drag.list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.offset ?: 0)
            drag.source = id; drag.target = id; drag.y = down.position.y
            haptics.grab()
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.singleOrNull()?.takeIf { it.id == down.id } ?: break
                    if (change.isConsumed) break
                    change.consume()
                    drag.y = change.position.y
                    if (drag.updateTarget(loadedNow)) haptics.tick()
                    if (!change.pressed) {
                        val target = drag.target
                        drag.reset()
                        if (change.changedToUpIgnoreConsumed() && id in loadedNow && target != null && target != id && target in loadedNow) {
                            haptics.drop(); move(id, target)
                        }
                        break
                    }
                }
            } finally { stopScroll.cancel(); drag.reset() }
        }
    }
}

@Composable
fun EchoOrderDragHandle(id: String, drag: EchoOrderDragState) {
    DisposableEffect(drag, id) { onDispose { drag.handles.remove(id) } }
    Box(Modifier.size(48.dp).onGloballyPositioned { drag.handles[id] = it.boundsInRoot() }, contentAlignment = Alignment.Center) {
        EchoIcon(Icons.Rounded.DragHandle, stringResource(R.string.echo_order_drag_handle), Modifier.size(24.dp))
    }
}
