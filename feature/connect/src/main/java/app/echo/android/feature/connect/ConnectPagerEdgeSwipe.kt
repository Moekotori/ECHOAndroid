package app.echo.android.feature.connect

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Observe outward drags at the two inner pager edges without consuming vertical scroll. */
@Composable
internal fun Modifier.connectPagerEdgeSwipe(
    pagerState: PagerState,
    onSwipeToLibrary: () -> Unit,
    onSwipeToDiagnostics: () -> Unit,
): Modifier {
    val latestLibrary = rememberUpdatedState(onSwipeToLibrary)
    val latestDiagnostics = rememberUpdatedState(onSwipeToDiagnostics)
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }

    return pointerInput(pagerState, threshold) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startingPage = pagerState.currentPage
            var horizontal = 0f
            var vertical = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                horizontal = pointer.position.x - down.position.x
                vertical = pointer.position.y - down.position.y
                if (!pointer.pressed) break
            }
            if (pagerState.currentPage == startingPage &&
                abs(horizontal) >= threshold && abs(horizontal) > abs(vertical) * 1.25f
            ) {
                when {
                    startingPage == 0 && horizontal > 0f -> latestLibrary.value()
                    startingPage == pagerState.pageCount - 1 && horizontal < 0f ->
                        latestDiagnostics.value()
                }
            }
        }
    }
}
