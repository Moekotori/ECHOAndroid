package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import app.echo.android.design.detectEchoTargetDrag

/** Long-press deliberately enters positioning, leaving ordinary swipes available to page scrolling. */
@Composable
internal fun Modifier.backgroundPositionDrag(
    sourceKey: String,
    aspectRatio: Float,
    scale: Float,
    positionX: Float,
    positionY: Float,
    onPreview: (Float, Float) -> Unit,
    onCommit: (Float, Float) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
): Modifier {
    val currentPosition by rememberUpdatedState(Offset(positionX, positionY))
    val currentRatio by rememberUpdatedState(aspectRatio)
    val currentScale by rememberUpdatedState(scale)
    val preview by rememberUpdatedState(onPreview)
    val commit by rememberUpdatedState(onCommit)
    val draggingChange by rememberUpdatedState(onDraggingChange)
    val haptic = LocalHapticFeedback.current
    return pointerInput(sourceKey) {
        var start = Offset.Zero
        var draft = Offset.Zero
        var overflow = Offset.Zero
        detectEchoTargetDrag(
            longPress = true,
            targetAt = { if (currentRatio > 0f) Unit else null },
            onStart = { _, _ ->
                start = currentPosition
                draft = start
                overflow = Offset(
                    maxOf(size.width.toFloat(), size.height * currentRatio) * currentScale - size.width,
                    maxOf(size.height.toFloat(), size.width / currentRatio) * currentScale - size.height,
                )
                draggingChange(true)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onDrag = { change, delta ->
                change.consume()
                draft = Offset(
                    backgroundPositionAfterDrag(draft.x, delta.x, overflow.x),
                    backgroundPositionAfterDrag(draft.y, delta.y, overflow.y),
                )
                preview(draft.x, draft.y)
            },
            onStop = {
                draggingChange(false)
                if (draft != start) commit(draft.x, draft.y)
            },
            onCancel = { draggingChange(false); preview(start.x, start.y) },
        )
    }
}

internal fun backgroundPositionAfterDrag(position: Float, delta: Float, overflow: Float): Float =
    if (overflow > .5f) (position + 2f * delta / overflow).coerceIn(-1f, 1f) else position
