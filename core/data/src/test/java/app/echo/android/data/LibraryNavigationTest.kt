package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryNavigationTest {
    @Test
    fun artistKeyMatchesLibraryAggregation() {
        val target = artistNavigationTarget("Eagles")
        assertEquals("eagles", target?.artistKey)
        assertEquals("Eagles", target?.name)
        assertEquals("eagles", artistNavigationTarget("Ｅａｇｌｅｓ")?.artistKey)
        assertEquals("eagles", artistNavigationTarget(" Eagles  ")?.artistKey)
    }

    @Test
    fun unknownAndVariousArtistsAreNotPages() {
        assertNull(artistNavigationTarget(""))
        assertNull(artistNavigationTarget("   "))
        assertNull(artistNavigationTarget("Unknown artist"))
        assertNull(artistNavigationTarget("未知艺术家"))
        assertNull(artistNavigationTarget("<unknown>"))
        assertNull(artistNavigationTarget("Various Artists"))
        assertNull(artistNavigationTarget("群星"))
        assertNull(artistNavigationTarget("オムニバス"))
    }
}
