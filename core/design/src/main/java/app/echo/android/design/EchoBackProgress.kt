package app.echo.android.design

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One draw-phase progress drives the foreground and its underlay, including cancelled gestures. */
class EchoBackProgress internal constructor(
    private val scope: CoroutineScope,
    private val lightweight: () -> Boolean,
) {
    var value by mutableFloatStateOf(0f)
        private set
    private var velocity = 0f
    private var recovery: Job? = null
    private var generation = 0

    fun update(progress: Float) {
        generation++
        recovery?.cancel()
        recovery = null
        velocity = 0f
        value = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    }

    fun restore(): Job? {
        val from = value
        val initialVelocity = velocity
        val owner = ++generation
        recovery?.cancel()
        if (from <= 0f) {
            value = 0f
            velocity = 0f
            recovery = null
            return null
        }
        return scope.launch {
            animate(
                initialValue = from,
                targetValue = 0f,
                initialVelocity = initialVelocity,
                animationSpec = if (lightweight()) tween(EchoMotion.PageLightweightMs) else EchoMotion.silkFloat(320),
            ) { progress, speed ->
                if (generation == owner) {
                    value = progress.coerceIn(0f, 1f)
                    velocity = speed
                }
            }
            if (generation == owner) {
                value = 0f
                velocity = 0f
            }
        }.also { recovery = it }
    }

    /** Clear only after presentation exit finishes, so reopening an in-flight exit can reverse it. */
    fun reset() = update(0f)
}

@Composable
fun rememberEchoBackProgress(lightweight: Boolean): EchoBackProgress {
    val scope = rememberCoroutineScope()
    val mode = rememberUpdatedState(lightweight)
    return remember(scope) { EchoBackProgress(scope) { mode.value } }
}
