package app.echo.android.feature.radio

import org.junit.Assert.assertEquals
import org.junit.Test

class RadioNavigationTest {
    @Test fun genreResultsReturnToTheirParentGrid() {
        assertEquals("music", RadioNavigation.parent("results", "vocaloid"))
        assertEquals("music", RadioNavigation.parent("results", "j-pop"))
        assertEquals("spoken", RadioNavigation.parent("results", "talk"))
        assertEquals("home", RadioNavigation.parent("results", ""))
        assertEquals("home", RadioNavigation.parent("music", "j-pop"))
    }
}
