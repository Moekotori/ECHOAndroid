package app.echo.android.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibrarySelectionStateTest {
    @Test fun hidingTheSelectedCategoryFallsBackBeforeRenderingAndKeepsRadioAccessible() {
        val layout = app.echo.android.model.settings.EchoLibraryLayout(
            hidden = app.echo.android.model.settings.EchoLibraryCategory.entries.toSet() -
                app.echo.android.model.settings.EchoLibraryCategory.Folders,
        )
        assertEquals(LibraryViewMode.Folders, restoredLibraryViewMode(LibraryViewMode.Songs.ordinal, false, layout))
        assertEquals(LibraryViewMode.Albums, restoredLibraryViewMode(LibraryViewMode.Folders.ordinal, true, layout))
        assertEquals(LibraryViewMode.Radio, restoredLibraryViewMode(LibraryViewMode.Radio.ordinal, false, layout))
    }

    @Test fun pagerUsesTheCustomizedOrderAndCloudKeepsItsSupportedCategory() {
        val layout = app.echo.android.model.settings.EchoLibraryLayout(
            order = app.echo.android.model.settings.EchoLibraryCategory.entries.reversed(),
            hidden = setOf(app.echo.android.model.settings.EchoLibraryCategory.Songs),
        )
        assertEquals(listOf(LibraryViewMode.Playlists, LibraryViewMode.Folders, LibraryViewMode.Artists, LibraryViewMode.Albums),
            libraryPagerModes(false, layout))
        assertEquals(listOf(LibraryViewMode.Albums), libraryPagerModes(true, layout))
    }

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

    @Test
    fun indicatorTracksFractionalPagesWithUnequalTabWidthsAndSpacing() {
        val widths = mapOf(0 to 80, 1 to 120, 2 to 60)
        assertEquals(40f, libraryTabInterpolatedCenterPx(widths, 0f, 3, 20f)!!, 0f)
        assertEquals(100f, libraryTabInterpolatedCenterPx(widths, 0.5f, 3, 20f)!!, 0f)
        assertEquals(215f, libraryTabInterpolatedCenterPx(widths, 1.5f, 3, 20f)!!, 0f)
        assertEquals(270f, libraryTabInterpolatedCenterPx(widths, 3f, 3, 20f)!!, 0f)
        assertNull(libraryTabInterpolatedCenterPx(mapOf(0 to 80), 0.5f, 3, 20f))
    }

    @Test
    fun tabRevealScrollOnlyMovesWhenSelectionIsClippedAndStaysInBounds() {
        assertEquals(40, libraryTabRevealScroll(40, 200, 160, 80f, 180f))
        assertEquals(120, libraryTabRevealScroll(40, 200, 160, 240f, 320f))
        assertEquals(20, libraryTabRevealScroll(120, 200, 160, 20f, 100f))
        assertEquals(160, libraryTabRevealScroll(0, 200, 160, 340f, 420f))
        assertEquals(60, libraryTabRevealScroll(0, 200, 160, 60f, 300f))
    }
}
