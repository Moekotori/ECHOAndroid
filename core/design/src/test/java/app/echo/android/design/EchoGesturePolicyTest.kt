package app.echo.android.design

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class EchoGesturePolicyTest {
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
