package app.echo.android.design

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker

/** Cancellation is separate from a successful release, including a zero-velocity release. */
@Composable
fun Modifier.echoDrag(
    orientation: Orientation,
    enabled: Boolean = true,
    gestureKey: Any? = null,
    onStart: () -> Unit = {},
    onDelta: (Float) -> Unit,
    onStop: (Float) -> Unit,
    onCancel: () -> Unit,
): Modifier {
    val start = rememberUpdatedState(onStart)
    val delta = rememberUpdatedState(onDelta)
    val stop = rememberUpdatedState(onStop)
    val cancel = rememberUpdatedState(onCancel)
    return pointerInput(orientation, enabled, gestureKey) {
        if (!enabled) return@pointerInput
        val tracker = VelocityTracker()
        var active = false
        try {
            detectDragGestures(
                orientationLock = orientation,
                onDragStart = { down, _, _ ->
                    tracker.resetTracking()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    active = true
                    start.value()
                    // detectDragGestures delivers overSlop in its first onDrag callback.
                },
                onDrag = { change, amount ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    delta.value(if (orientation == Orientation.Horizontal) amount.x else amount.y)
                },
                onDragEnd = { change ->
                    val releaseDelta = change.position - change.previousPosition
                    val finalDelta = if (orientation == Orientation.Horizontal) releaseDelta.x else releaseDelta.y
                    if (finalDelta != 0f) delta.value(finalDelta)
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val velocity = tracker.calculateVelocity()
                    active = false
                    stop.value(if (orientation == Orientation.Horizontal) velocity.x else velocity.y)
                },
                onDragCancel = { active = false; cancel.value() },
            )
        } finally {
            // Disabling or disposing pointer input cancels its coroutine as well.
            if (active) cancel.value()
        }
    }
}

/** Hit-test the initial down before any movement is consumed. */
suspend fun <T : Any> PointerInputScope.detectEchoTargetDrag(
    longPress: Boolean = false,
    targetAt: (Offset) -> T?,
    onStart: (T, Offset) -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val target = targetAt(down.position) ?: return@awaitEachGesture
        var active = false
        try {
            var overSlop = Offset.Zero
            val start = if (longPress) awaitLongPressOrCancellation(down.id)
                else awaitTouchSlopOrCancellation(down.id) { change, over ->
                    overSlop = over
                    change.consume()
                }
            if (start == null) return@awaitEachGesture
            active = true
            onStart(target, start.position)
            if (!longPress) onDrag(start, overSlop)
            val completed = drag(start.id) { change ->
                onDrag(change, change.position - change.previousPosition)
                change.consume()
            }
            if (completed) {
                currentEvent.changes.forEach {
                    if (!it.pressed) it.consume()
                }
                active = false
                onStop()
            }
        } finally {
            if (active) onCancel()
        }
    }
}
