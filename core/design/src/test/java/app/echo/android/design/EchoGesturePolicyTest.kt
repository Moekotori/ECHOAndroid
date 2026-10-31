package app.echo.android.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import org.junit.Assert.*
import org.junit.Test

class EchoGesturePolicyTest {
    @Test fun verticalDragCannotSwitchAxisOnHorizontalJitterOrAtTheEdge() {
        val axis = EchoPagerDragAxis()
        assertFalse(axis.ownsScroll(Offset.Zero, Offset(2f, 24f)))
        assertFalse(axis.ownsScroll(Offset(0f, 1f), Offset(6f, 0f)))
        assertFalse(axis.ownsScroll(Offset.Zero, Offset(12f, 0f)))
        axis.reset()
        assertTrue(axis.ownsScroll(Offset.Zero, Offset(24f, 2f)))
    }

    @Test fun horizontalDragKeepsItsAxisAndEmptyEventsDoNotLockIt() {
        val axis = EchoPagerDragAxis()
        assertFalse(axis.ownsScroll(Offset.Zero, Offset.Zero))
        assertTrue(axis.ownsScroll(Offset.Zero, Offset(24f, 2f)))
        assertTrue(axis.ownsScroll(Offset.Zero, Offset(1f, 6f)))
        axis.reset()
        assertFalse(axis.ownsScroll(Offset.Zero, Offset(2f, 24f)))
    }

    @Test fun verticalFlingResidueCannotStartPaging() {
        assertFalse(echoPagerOwnsFling(Velocity(0f, 2000f), Velocity(300f, 0f), 0f))
        assertFalse(echoPagerOwnsFling(Velocity.Zero, Velocity(300f, 1800f), 0f))
    }

    @Test fun displacedPagerSettlesEvenAfterZeroVelocityRelease() {
        assertTrue(echoPagerOwnsFling(Velocity.Zero, Velocity.Zero, 0.12f))
        assertTrue(echoPagerOwnsFling(Velocity(0f, 2000f), Velocity.Zero, 0.12f))
    }

    @Test fun intentionalHorizontalFlingKeepsPaging() {
        assertTrue(echoPagerOwnsFling(Velocity.Zero, Velocity(-900f, 50f), 0f))
        assertFalse(echoPagerOwnsFling(Velocity.Zero, Velocity(5f, 0f), 0f))
    }
    @Test fun verticalChildDoesNotLoseScrollToHorizontalResidue() {
        assertFalse(echoHorizontalGestureOwnsScroll(Offset(0f, 24f), Offset(3f, 0f)))
    }

    @Test fun horizontalChildKeepsDiagonalResidue() {
        assertTrue(echoHorizontalGestureOwnsScroll(Offset(-24f, 0f), Offset(0f, 3f)))
    }

    @Test fun equalDiagonalMovementPrefersVerticalContent() {
        assertFalse(echoHorizontalGestureOwnsScroll(Offset.Zero, Offset(10f, -10f)))
    }

    @Test fun nestedPagingUsesTheSameDirectionForDraggingAndFlinging() {
        assertEquals(-1f, echoPagerScrollSign(rtl = false, reverseLayout = false), 0f)
        assertEquals(1f, echoPagerScrollSign(rtl = true, reverseLayout = false), 0f)
        assertEquals(1f, echoPagerScrollSign(rtl = false, reverseLayout = true), 0f)
        assertEquals(-1f, echoPagerScrollSign(rtl = true, reverseLayout = true), 0f)
    }
}
