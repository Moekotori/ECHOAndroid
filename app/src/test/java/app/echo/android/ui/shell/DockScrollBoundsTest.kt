package app.echo.android.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class DockScrollBoundsTest {
    @Test
    fun flingCannotCrossFromHomeIntoSettings() {
        assertEquals(0f, boundedDockScrollDelta(1f, -800f, 1000f), 0.01f)
        assertEquals(-100f, boundedDockScrollDelta(1.1f, -800f, 1000f), 0.01f)
    }

    @Test
    fun lastTabClipsOnlyTheExcessAndInteriorMotionKeepsItsDistance() {
        assertEquals(100f, boundedDockScrollDelta(3.9f, 800f, 1000f), 0.01f)
        assertEquals(-350f, boundedDockScrollDelta(2.4f, -350f, 1000f), 0.01f)
        assertEquals(0f, boundedDockScrollDelta(2f, 350f, 0f), 0f)
    }
}
