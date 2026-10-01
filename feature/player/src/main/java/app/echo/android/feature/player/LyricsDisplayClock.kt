package app.echo.android.feature.player

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import kotlinx.coroutines.isActive
import kotlin.math.abs

/** Bounded display interpolation. Host changes, pause and seeking always re-anchor the display. */
internal class LyricsDisplayClock {
    private var hostPosition = Long.MIN_VALUE
    private var sampledAt = 0L
    private var correction = 0.0
    private var displayed = 0L
    private var previousSpeed = 1f
    fun position(host: Long, now: Long, playing: Boolean, speed: Float): Long {
        val elapsed = (now - sampledAt).coerceIn(0L, 650L)
        if (hostPosition == Long.MIN_VALUE || !playing || speed != previousSpeed) {
            hostPosition = host
            sampledAt = now
            correction = 0.0
            displayed = host
        } else if (host != hostPosition) {
            val predicted = hostPosition + elapsed * speed + correction * (1.0 - elapsed / 180.0).coerceAtLeast(0.0)
            // Small forward sample jitter converges over 180 ms without reversing
            // the wipe. Backward seeks and large discontinuities stay immediate.
            val continuous = host >= hostPosition && now - sampledAt <= 650L && abs(host - predicted) <= 160.0
            correction = if (continuous) predicted - host else 0.0
            if (!continuous) displayed = host
            hostPosition = host
            sampledAt = now
        }
        previousSpeed = speed
        val age = (now - sampledAt).coerceIn(0L, 650L)
        val target = if (playing) (host + age * speed + correction * (1.0 - age / 180.0).coerceAtLeast(0.0)).toLong() else host
        displayed = if (playing) maxOf(displayed, target).coerceAtLeast(0L) else host.coerceAtLeast(0L)
        return displayed
    }
}

internal fun lyricsInterpolationFps(mode: EchoEffectivePerformanceMode): Int = when (mode) {
    EchoEffectivePerformanceMode.Lightweight -> 0
    EchoEffectivePerformanceMode.Balanced -> 60
    EchoEffectivePerformanceMode.HighPerformance -> 120
}

/** Keep a deadline rather than counting vsyncs, including on 90 Hz displays. */
internal class LyricsFrameGate(fps: Int) {
    private val intervalNs = 1_000_000_000L / fps.coerceIn(1, 120)
    private var nextNs = Long.MIN_VALUE

    fun accept(frameNs: Long): Boolean {
        if (nextNs == Long.MIN_VALUE) {
            nextNs = frameNs + intervalNs
            return true
        }
        val lateNs = frameNs - nextNs + 500_000L
        if (lateNs < 0L) return false
        // Skip missed deadlines after a stall; never emit a burst of catch-up updates.
        nextNs += (lateNs / intervalNs + 1L) * intervalNs
        return true
    }
}

@Composable
internal fun rememberLyricsDisplayPosition(
    host: State<Long>, trackKey: String?, playing: Boolean, speed: Float, enabled: Boolean,
): State<Long> {
    val displayed = remember(host, trackKey) { mutableLongStateOf(host.value) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focused = LocalWindowInfo.current.isWindowFocused
    val fps = lyricsInterpolationFps(LocalEchoEffectivePerformanceMode.current)
    val interpolate = enabled && playing && focused && fps > 0
    LaunchedEffect(host, trackKey, speed, interpolate, fps, lifecycle) {
        displayed.longValue = host.value
        if (interpolate) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val clock = LyricsDisplayClock()
                val gate = LyricsFrameGate(fps)
                while (isActive) {
                    withFrameNanos { frameNs ->
                        if (gate.accept(frameNs)) {
                            displayed.longValue = clock.position(host.value, SystemClock.elapsedRealtime(), true, speed)
                        }
                    }
                }
            }
        }
    }
    return if (interpolate) displayed else host
}
