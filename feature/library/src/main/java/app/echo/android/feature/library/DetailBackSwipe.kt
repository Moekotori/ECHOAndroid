package app.echo.android.feature.library

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Only an inward horizontal drag from the existing 24 dp start edge can leave a detail page. */
@Composable
internal fun Modifier.detailBackSwipe(onBack: () -> Unit): Modifier {
    val back = rememberUpdatedState(onBack)
    val density = LocalDensity.current
    val edge = with(density) { 24.dp.toPx() }
    val threshold = with(density) { 64.dp.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return pointerInput(edge, threshold, rtl) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (currentEvent.changes.size != 1) return@awaitEachGesture
            if (if (rtl) down.position.x < size.width - edge else down.position.x > edge) return@awaitEachGesture
            val direction = if (rtl) -1f else 1f
            var dragging = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.singleOrNull()?.takeIf { it.id == down.id } ?: break
                if (change.isConsumed) break // The child list, carousel, or system gesture keeps it.
                val delta = change.position - down.position
                val distance = delta.x * direction
                if (!change.pressed) {
                    if (dragging && change.changedToUpIgnoreConsumed()) {
                        change.consume()
                        if (distance >= threshold) back.value()
                    }
                    break
                }
                if (!dragging) {
                    if (delta.getDistance() <= viewConfiguration.touchSlop) continue
                    if (distance <= abs(delta.y)) break
                    dragging = true
                }
                change.consume()
            }
        }
    }
}
