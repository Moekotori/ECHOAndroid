package app.echo.android.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import kotlinx.coroutines.isActive

internal fun fluidBackdropFps(mode: EchoEffectivePerformanceMode): Int = when (mode) {
    EchoEffectivePerformanceMode.Lightweight -> 0
    EchoEffectivePerformanceMode.Balanced -> 24
    EchoEffectivePerformanceMode.HighPerformance -> 30
}

/** Restarts its timestamp after hiding/resuming, keeping the surface frozen while invisible. */
@Composable
internal fun rememberFluidBackdropTime(visible: Boolean, fps: Int): State<Long> {
    val elapsed = remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(visible, fps, lifecycle) {
        if (!visible || fps <= 0) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val clock = FluidBackdropClock(elapsed.longValue)
            val gate = LyricsFrameGate(fps)
            while (isActive) {
                withFrameNanos { frameNs ->
                    if (gate.accept(frameNs)) elapsed.longValue = clock.advance(frameNs)
                }
            }
        }
    }
    return elapsed
}

/** Decorative time only; never reads or publishes playback position. */
internal class FluidBackdropClock(initialMs: Long = 0L) {
    private var elapsedNs = initialMs * 1_000_000L
    private var previousNs = Long.MIN_VALUE

    fun advance(frameNs: Long): Long {
        if (previousNs != Long.MIN_VALUE) elapsedNs += (frameNs - previousNs).coerceIn(0L, 100_000_000L)
        previousNs = frameNs
        return elapsedNs / 1_000_000L
    }
}
