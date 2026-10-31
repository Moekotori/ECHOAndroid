package app.echo.android.design

import org.junit.Assert.*
import org.junit.Test

class EchoDragAutoScrollTest {
    private fun speed(y: Float, backward: Boolean = true, forward: Boolean = true) =
        echoDragEdgeSpeed(y, 0f, 400f, 48f, 750f, backward, forward)

    @Test fun centerAndExhaustedEdgesDoNotStartScrolling() {
        assertEquals(0f, speed(200f), 0f)
        assertEquals(0f, speed(-10f, backward = false), 0f)
        assertEquals(0f, speed(420f, forward = false), 0f)
        assertEquals(0f, echoDragEdgeSpeed(0f, 0f, 0f, 48f, 750f, true, true), 0f)
    }

    @Test fun edgeSpeedRampsUpAndStaysBoundedOutsideTheList() {
        assertEquals(-187.5f, speed(24f), 0.001f)
        assertEquals(187.5f, speed(376f), 0.001f)
        assertEquals(-750f, speed(-500f), 0f)
        assertEquals(750f, speed(900f), 0f)
        assertEquals(0f, echoDragEdgeSpeed(20f, 0f, 40f, 48f, 750f, true, true), 0f)
    }
}
