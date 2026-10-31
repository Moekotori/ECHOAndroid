package app.echo.android.design

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

/** Signed px/s. Content padding can reserve the bottom region for the floating playback dock. */
internal fun echoDragEdgeSpeed(
    y: Float, top: Float, bottom: Float, edge: Float, maxSpeed: Float,
    canScrollBackward: Boolean, canScrollForward: Boolean,
): Float {
    val band = edge.coerceAtMost((bottom - top) / 2f)
    if (band <= 0f) return 0f
    val pull = when {
        y < top + band && canScrollBackward -> -((top + band - y) / band).coerceIn(0f, 1f)
        y > bottom - band && canScrollForward -> ((y - bottom + band) / band).coerceIn(0f, 1f)
        else -> 0f
    }
    return pull * abs(pull) * maxSpeed
}

/** No frame loop in the middle of a list, after release, or at an exhausted scroll boundary. */
@Composable
fun EchoDragAutoScroll(
    list: LazyListState,
    dragging: Boolean,
    pointerY: () -> Float,
    onScrolled: () -> Unit,
) {
    val pointer by rememberUpdatedState(pointerY)
    val scrolled by rememberUpdatedState(onScrolled)
    val density = LocalDensity.current
    val edge = with(density) { 48.dp.toPx() }
    val maxSpeed = with(density) { 750.dp.toPx() }
    LaunchedEffect(list, dragging, edge, maxSpeed) {
        if (!dragging) return@LaunchedEffect
        fun speed(): Float {
            val layout = list.layoutInfo
            val top = layout.viewportStartOffset.coerceAtLeast(0).toFloat()
            val bottom = layout.viewportEndOffset - minOf(layout.afterContentPadding,
                (layout.viewportEndOffset - layout.viewportStartOffset) / 2)
            return echoDragEdgeSpeed(pointer(), top, bottom.toFloat(), edge, maxSpeed,
                list.canScrollBackward, list.canScrollForward)
        }
        snapshotFlow { speed() != 0f }.collectLatest { atEdge ->
            if (!atEdge) return@collectLatest
            var previousFrame = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceIn(0f, 0.032f)
                previousFrame = frame
                val velocity = speed()
                if (velocity == 0f) break
                if (list.scrollBy(velocity * seconds) != 0f) scrolled()
            }
        }
    }
}
