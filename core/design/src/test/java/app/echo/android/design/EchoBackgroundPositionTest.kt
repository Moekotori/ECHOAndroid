package app.echo.android.design

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class EchoBackgroundPositionTest {
    @Test fun imageCropUsesTheSamePhysicalDirectionsEvenInRtl() {
        val alignment = backgroundImageAlignment(1f, -1f)
        for (direction in LayoutDirection.entries) {
            assertEquals(IntOffset(0, -400), alignment.align(IntSize(600, 800), IntSize(400, 400), direction))
        }
    }

    @Test fun pansFromEdgeToEdgeWithoutExposingTheViewport() {
        assertEquals(-200, backgroundPanOffset(400, 600, -1f))
        assertEquals(-100, backgroundPanOffset(400, 600, 0f))
        assertEquals(0, backgroundPanOffset(400, 600, 1f))
        assertEquals(0, backgroundPanOffset(400, 400, 1f))
        assertEquals(-200, backgroundPanOffset(400, 600, -10f))
        assertEquals(0, backgroundPanOffset(400, 600, 10f))
        assertEquals(-100, backgroundPanOffset(400, 600, Float.NaN))
    }
}
