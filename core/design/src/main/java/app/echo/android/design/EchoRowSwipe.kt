package app.echo.android.design

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Claims a single finger only after a clear horizontal start in the requested direction. */
@Composable
fun Modifier.echoRowSwipe(
    enabled: Boolean,
    towardEnd: Boolean,
    gestureKey: Any?,
    onDrag: (Float) -> Unit,
    onRelease: (Float) -> Unit,
    onCancel: () -> Unit,
): Modifier {
    if (!enabled || !LocalEchoPageInteractive.current) return this
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val direction = if (towardEnd != rtl) 1f else -1f
    val drag = rememberUpdatedState(onDrag)
    val release = rememberUpdatedState(onRelease)
    val cancel = rememberUpdatedState(onCancel)
    return pointerInput(gestureKey, direction) {
        val edge = 24.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (currentEvent.changes.size != 1) return@awaitEachGesture

            val insideRow = down.position.x >= edge && down.position.x <= size.width - edge
            val slop = viewConfiguration.touchSlop
            var pastSlop = false
            var dragging = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.singleOrNull()?.takeIf { it.id == down.id } ?: break
                    if (change.isConsumed) break
                    val movement = change.position - down.position
                    val moved = abs(movement.x) >= slop || abs(movement.y) >= slop
                    val progress = (movement.x * direction - slop).coerceAtLeast(0f)
                    if (!change.pressed) {
                        // An unclaimed drag within the row must not finish as a click.
                        if (pastSlop || moved) change.consume()
                        if (dragging) {
                            dragging = false
                            release.value(progress)
                        }
                        break
                    }
                    if (!pastSlop && moved) {
                        pastSlop = true
                        dragging = insideRow && echoRowSwipeStarts(movement, direction, slop)
                    }
                    if (dragging) {
                        awaitPointerEvent(PointerEventPass.Main)
                        if (change.isConsumed) break
                        change.consume()
                        drag.value(progress)
                    } else {
                        // Leave movement with the scroll or navigation owner and let it finish.
                        awaitPointerEvent(PointerEventPass.Final)
                        if (change.isConsumed) break
                    }
                }
            } finally {
                if (dragging) cancel.value()
            }
        }
    }
}

internal fun echoRowSwipeStarts(movement: Offset, direction: Float, touchSlop: Float): Boolean {
    val distance = movement.x * direction
    return distance >= touchSlop && distance > abs(movement.y) * 1.2f
}
