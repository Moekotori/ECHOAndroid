package app.echo.android.feature.library

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Only a rightward drag starting at the left edge can leave a detail page. */
@Composable
internal fun Modifier.detailBackSwipe(onBack: () -> Unit): Modifier {
    val back = rememberUpdatedState(onBack)
    val density = LocalDensity.current
    val edge = with(density) { 24.dp.toPx() }
    val threshold = with(density) { 64.dp.toPx() }
    return pointerInput(edge, threshold) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (down.position.x > edge) return@awaitEachGesture
            val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                if (overSlop > 0f) change.consume()
            } ?: return@awaitEachGesture
            var distance = start.position.x - down.position.x
            val completed = horizontalDrag(start.id) { change ->
                distance += change.position.x - change.previousPosition.x
                change.consume()
            }
            if (completed && distance >= threshold) back.value()
        }
    }
}
