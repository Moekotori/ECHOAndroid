package app.echo.android.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import org.junit.Assert.*
import org.junit.Test

class EchoGesturePolicyTest {
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
}
