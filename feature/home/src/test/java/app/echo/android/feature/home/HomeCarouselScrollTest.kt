package app.echo.android.feature.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCarouselScrollTest {
    @Test
    fun edgeDragsAndFlingFramesKeepHorizontalRemainderInsideRow() {
        for (source in listOf(NestedScrollSource.UserInput, NestedScrollSource.SideEffect)) {
            for (horizontal in listOf(-120f, 120f)) {
                assertEquals(
                    Offset(horizontal, 0f),
                    HomeCarouselNestedScroll.onPostScroll(
                        Offset.Zero, Offset(horizontal, 18f), source,
                    ),
                )
            }
        }
    }

    @Test
    fun edgeFlingsKeepVelocityInsideRowInBothDirections() = runBlocking {
        for (horizontal in listOf(-8000f, 8000f)) {
            assertEquals(
                Velocity(horizontal, 0f),
                HomeCarouselNestedScroll.onPostFling(
                    Velocity.Zero, Velocity(horizontal, 300f),
                ),
            )
        }
    }

    @Test
    fun rowRetainsItsOwnFlingAndVerticalScrollingPassesThrough() = runBlocking {
        assertEquals(Velocity.Zero, HomeCarouselNestedScroll.onPreFling(Velocity(8000f, 300f)))
        assertEquals(
            Offset.Zero,
            HomeCarouselNestedScroll.onPostScroll(
                Offset.Zero, Offset(0f, 100f), NestedScrollSource.UserInput,
            ),
        )
        assertEquals(
            Velocity.Zero,
            HomeCarouselNestedScroll.onPostFling(Velocity.Zero, Velocity(0f, 2000f)),
        )
    }
}
