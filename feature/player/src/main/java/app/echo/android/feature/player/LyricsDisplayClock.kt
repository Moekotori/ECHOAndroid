package app.echo.android.feature.player

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive

/** Bounded display interpolation. Host changes, pause and seeking always re-anchor the display. */
internal class LyricsDisplayClock {
    private var hostPosition = Long.MIN_VALUE
    private var sampledAt = 0L
    fun position(host: Long, now: Long, playing: Boolean, speed: Float): Long {
        if (host != hostPosition || !playing) {
            hostPosition = host
            sampledAt = now
        }
        val advance = if (playing) ((now - sampledAt).coerceIn(0L, 650L) * speed).toLong() else 0L
        return (host + advance).coerceAtLeast(0L)
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
