package app.echo.android.feature.player

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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

@Composable
internal fun rememberLyricsDisplayPosition(
    host: State<Long>, trackKey: String?, playing: Boolean, speed: Float, enabled: Boolean,
): State<Long> {
    val displayed = remember(trackKey) { mutableLongStateOf(host.value) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(host, trackKey, playing, speed, enabled, lifecycle) {
        displayed.longValue = host.value
        if (enabled && playing) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val clock = LyricsDisplayClock()
                while (isActive) {
                    withFrameNanos {
                        displayed.longValue = clock.position(host.value, SystemClock.elapsedRealtime(), true, speed)
                    }
                }
            }
        }
    }
    return if (enabled && playing) displayed else host
}
