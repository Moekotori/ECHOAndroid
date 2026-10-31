package app.echo.android.feature.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.echo.android.design.rememberEchoHapticPerformer

/** Local rail input: vertical motion remains available to lists and the player's dismiss gesture. */
@Composable
internal fun Modifier.playerSeekInput(
    trackKey: String?,
    durationMs: Long,
    enabled: Boolean,
    inset: Dp = 0.dp,
    rtl: Boolean = false,
    onPressed: (Boolean) -> Unit = {},
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    onCancel: () -> Unit,
): Modifier {
    val feedback = rememberEchoHapticPerformer()
    val pressed by rememberUpdatedState(onPressed)
    val preview by rememberUpdatedState(onPreview)
    val commit by rememberUpdatedState(onCommit)
    val cancel by rememberUpdatedState(onCancel)
    return pointerInput(trackKey, durationMs, enabled, inset, rtl, feedback) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown()
            if (currentEvent.changes.size != 1) return@awaitEachGesture
            val gesture = PlayerSeekGesture(
                down.position.x, down.position.y, viewConfiguration.touchSlop,
                fractionAt = { playerSeekFraction(it, size.width.toFloat(), inset.toPx(), rtl) },
                onStart = { feedback.grab() },
                onPreview = { preview(it); feedback.seek(it) },
                onCommit = { commit(it); feedback.endSeek(committed = true) },
                onCancel = { cancel(); feedback.endSeek(committed = false) },
            )
            // Leave DOWN observable: the player's surface dismiss observer needs it through Final.
            // Consume only an acquired horizontal drag or a successful tap's UP.
            try {
                pressed(true)
                while (!gesture.finished) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    // Never transfer a seek to another finger or commit after a parent takes it.
                    if (change == null || change.isConsumed || event.changes.size != 1) break
                    if (!change.pressed) {
                        val inside = change.position.x >= 0f && change.position.x <= size.width &&
                            change.position.y >= 0f && change.position.y <= size.height
                        if (gesture.release(change.position.x, change.position.y, inside)) change.consume()
                        break
                    }
                    if (gesture.move(change.position.x, change.position.y)) change.consume()
                    if (!gesture.dragging && !gesture.finished) {
                        // A parent can claim vertical scrolling later in this same event's Main pass.
                        val final = awaitPointerEvent(PointerEventPass.Final)
                        if (final.changes.any { it.id == down.id && it.isConsumed }) break
                    }
                }
            } finally {
                gesture.cancel()
                pressed(false)
            }
        }
    }
}
