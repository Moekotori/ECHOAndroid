package app.echo.android.feature.home

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity

internal object HomeCarouselNestedScroll : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = Offset(available.x, 0f)

    // Let the carousel run its own fling first, then swallow velocity left at either edge.
    // SideEffect scroll deltas above also stay here for the entire fling.
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        Velocity(available.x, 0f)
}

/**
 * Horizontal gestures belong to this whole row, including padding and single/empty cards.
 * Put this before horizontalScroll so its pointer handler gets movement after the child:
 * scrolling children take priority; otherwise the fallback consumes horizontal drags.
 * Taps and vertical movement remain available to cards and the Home list.
 */
internal fun Modifier.homeCarouselScroll(): Modifier = fillMaxWidth()
    .nestedScroll(HomeCarouselNestedScroll)
    .pointerInput(Unit) {
        detectHorizontalDragGestures { change, _ -> change.consume() }
    }
