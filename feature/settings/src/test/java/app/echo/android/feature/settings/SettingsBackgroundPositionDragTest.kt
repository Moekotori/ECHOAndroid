package app.echo.android.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsBackgroundPositionDragTest {
    @Test fun movementMatchesTheAvailableCropAndStopsAtTheEdge() {
        assertEquals(.5f, backgroundPositionAfterDrag(0f, 25f, 100f), .001f)
        assertEquals(-.5f, backgroundPositionAfterDrag(0f, -25f, 100f), .001f)
        assertEquals(1f, backgroundPositionAfterDrag(.5f, 100f, 100f), .001f)
        assertEquals(-1f, backgroundPositionAfterDrag(-.5f, -100f, 100f), .001f)
        assertEquals(.25f, backgroundPositionAfterDrag(.25f, 100f, 0f), .001f)
    }
}
