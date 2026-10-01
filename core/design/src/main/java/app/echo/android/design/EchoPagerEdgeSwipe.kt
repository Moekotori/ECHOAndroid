package app.echo.android.design

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal class EchoPagerEdgeDrag {
    var startingPage = -1
        private set
    private var distance = 0f
    fun begin(page: Int) { startingPage = page; distance = 0f }
    fun reset() { startingPage = -1; distance = 0f }
    fun add(consumed: Offset, available: Offset, pageCount: Int): Float {
        if (startingPage < 0) return 0f
        if (!echoHorizontalGestureOwnsScroll(consumed, available)) {
            if (consumed.y != 0f || available.y != 0f) distance = 0f
            return 0f
        }
        val outward = when {
            startingPage == 0 && available.x > 0f -> available.x
            startingPage == pageCount - 1 && available.x < 0f -> available.x
            else -> 0f
        }
        // Horizontal child controls keep ownership; only untouched edge movement counts.
        if (abs(consumed.x) > 0.5f) { distance = 0f; return 0f }
        if (outward == 0f) { distance = 0f; return 0f }
        distance += outward
        return outward
    }
    fun destination(currentPage: Int, pageCount: Int, threshold: Float): Int = when {
        currentPage != startingPage -> 0
        startingPage == 0 && distance >= threshold -> -1
        startingPage == pageCount - 1 && distance <= -threshold -> 1
        else -> 0
    }
}

/** Count only unconsumed nested scroll, and commit only on an actual finger release. */
@Composable
fun Modifier.echoPagerEdgeSwipe(
    pager: PagerState,
    onPrevious: () -> Unit,
    onNext: () -> Unit = {},
): Modifier {
    val previous = rememberUpdatedState(onPrevious)
    val next = rememberUpdatedState(onNext)
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val edge = remember(pager) { EchoPagerEdgeDrag() }
    val connection = remember(pager, edge, rtl) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val direction = if (rtl) -1f else 1f
                val used = edge.add(
                    consumed.copy(x = consumed.x * direction),
                    available.copy(x = available.x * direction),
                    pager.pageCount,
                )
                return Offset(used * direction, 0f)
            }
        }
    }
    return nestedScroll(connection).pointerInput(pager, threshold, rtl) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (pager.isScrollInProgress || abs(pager.currentPageOffsetFraction) > 0.001f) return@awaitEachGesture
            edge.begin(pager.settledPage)
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    if (event.changes.count { it.pressed } > 1) break
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!pointer.pressed) {
                        if (pointer.changedToUpIgnoreConsumed() && abs(pager.currentPageOffsetFraction) < 0.001f) {
                            when (edge.destination(pager.currentPage, pager.pageCount, threshold)) {
                                -1 -> previous.value()
                                1 -> next.value()
                            }
                        }
                        break
                    }
                }
            } finally { edge.reset() }
        }
    }
}
