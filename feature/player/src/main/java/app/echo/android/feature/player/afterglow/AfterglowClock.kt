package app.echo.android.feature.player.afterglow

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** This state is read by the stage's draw lambda, never by the surrounding player composition. */
@Composable
internal fun rememberAfterglowPosition(
    host: State<Long>, trackKey: String?, playing: Boolean, speed: Float, visible: Boolean, fps: Int,
): State<Long> {
    val displayed = remember(host, trackKey) { mutableLongStateOf(host.value) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(host, trackKey, playing, speed, visible, fps, lifecycle) {
        displayed.longValue = host.value
        if (!visible) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val clock = AfterglowClock()
            // Host events still update paused / reduced-motion views, including seeks and offsets.
            launch { snapshotFlow { host.value }.collect {
                // While interpolating, host events only re-anchor: state publication belongs to the gate.
                // This avoids adding host-triggered draws on top of the 30/60 fps stage budget.
                if (!playing || fps <= 0) displayed.longValue = it
            } }
            if (playing && fps > 0) {
                val gate = AfterglowFrameGate(fps)
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
    return displayed
}
