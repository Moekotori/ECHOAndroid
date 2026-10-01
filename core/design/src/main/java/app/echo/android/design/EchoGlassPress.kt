package app.echo.android.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** Observe presses without consuming input owned by buttons, dock paging or track swipes. */
@Composable
internal fun Modifier.echoGlassPress(): Modifier {
    if (LocalEchoEffectivePerformanceMode.current.isLightweight) return this
    var pressed by remember { mutableStateOf(false) }
    val compression = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 650f),
        label = "glass-press",
    )
    return this
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                try {
                    pressed = true
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pointer = event.changes.firstOrNull { it.id == down.id }
                        val held = pointer != null && pointer.pressed &&
                            (pointer.position - down.position).getDistance() < viewConfiguration.touchSlop
                        if (!held) break
                    } while (true)
                } finally {
                    pressed = false
                }
            }
        }
        .graphicsLayer {
            scaleX = 1f - compression.value * 0.006f
            scaleY = 1f - compression.value * 0.025f
        }
}
