package app.echo.android.feature.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember

/**
 * A 0..1 loop clock for decoration. Read it only in a draw block so it never recomposes anything;
 * while [running] is false it rests at a fixed value and schedules no frames.
 */
@Composable
internal fun rememberLoopClock(running: Boolean, seconds: Int): State<Float> {
    if (!running) return remember { mutableFloatStateOf(0.35f) }
    return rememberInfiniteTransition(label = "style-loop-clock").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(seconds * 1000, easing = LinearEasing), RepeatMode.Restart),
        label = "style-loop-clock-value",
    )
}
