package app.echo.android.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibrarySelectionStateTest {
    @Test
    fun coldStartOnCloudResolvesDefaultAndSavedCategoriesToAlbums() {
        for (mode in LibraryViewMode.entries) {
            val restored = restoredLibraryViewMode(mode.ordinal, cloudOnly = true)
            assertEquals(LibraryViewMode.Albums, restored)
            assertEquals(0, libraryPagerModes(cloudOnly = true).indexOf(restored))
        }
    }

    @Test
    fun invalidSavedCategoryFallsBackWithoutIndexingOutsideEnum() {
        for (index in listOf(-1, Int.MAX_VALUE)) {
            assertEquals(LibraryViewMode.Songs, restoredLibraryViewMode(index, cloudOnly = false))
            assertEquals(LibraryViewMode.Albums, restoredLibraryViewMode(index, cloudOnly = true))
        }
        assertEquals(LibraryViewMode.Songs, restoredLibraryViewMode(LibraryViewMode.Cloud.ordinal, false))
        assertEquals(LibraryViewMode.Artists, restoredLibraryViewMode(LibraryViewMode.Artists.ordinal, false))
    }

    @Test
    fun missingCloudTabCannotReadNegativeWidthKey() {
        val absent = libraryPagerModes(true).indexOf(LibraryViewMode.Songs)
        assertNull(libraryTabIndicatorCenterPx(emptyMap(), absent, 1))
        assertNull(libraryTabIndicatorCenterPx(mapOf(0 to 80), 1, 1))
        assertNull(libraryTabIndicatorCenterPx(emptyMap(), 0, 0))
        assertNull(libraryTabIndicatorCenterPx(mapOf(1 to 80), 1, 2))
        assertEquals(120f, libraryTabIndicatorCenterPx(mapOf(0 to 80, 1 to 80), 1, 2)!!, 0f)
    }
}
