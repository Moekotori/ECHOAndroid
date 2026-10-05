package app.echo.android.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryAdaptiveGridTest {
    @Test fun columnsFollowTheMeasuredPaneAndKeepPhoneDensity() {
        assertEquals(3, libraryWallColumns(312f, 1f))
        assertEquals(3, libraryWallColumns(392f, 1f))
        assertEquals(5, libraryWallColumns(760f, 1f))
        assertEquals(6, libraryWallColumns(1280f, 1f))
        assertEquals(2, libraryWallColumns(392f, 1.3f))
        assertEquals(1, libraryWallColumns(220f, 2f))
    }
}
