package app.echo.android.feature.library

import app.echo.android.model.library.LibraryTrackSortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTrackSortingTest {
    @Test fun everydaySortsAppearBeforeReverseAndSpecialistOrders() {
        assertEquals(listOf(LibraryTrackSortMode.Title, LibraryTrackSortMode.RecentlyUpdated,
            LibraryTrackSortMode.Artist, LibraryTrackSortMode.Album, LibraryTrackSortMode.FrequentlyPlayed,
            LibraryTrackSortMode.RecentlyPlayed, LibraryTrackSortMode.Random, LibraryTrackSortMode.FileName),
            LocalTrackSortOptions.take(8))
        val displayed = LocalTrackSortOptions.groupBy(::trackSortSection).values.flatten()
        assertEquals(LocalTrackSortOptions, displayed)
        assertTrue(displayed.indexOf(LibraryTrackSortMode.Composer) < displayed.indexOf(LibraryTrackSortMode.ComposerDescending))
        assertTrue(displayed.indexOf(LibraryTrackSortMode.FileName) < displayed.indexOf(LibraryTrackSortMode.FileNameDescending))
    }

    @Test fun localMenuIncludesEverySortExactlyOnceAndNewMetadataSortsStayLocal() {
        assertEquals(LibraryTrackSortMode.entries.toSet(), LocalTrackSortOptions.toSet())
        assertEquals(LocalTrackSortOptions.size, LocalTrackSortOptions.distinct().size)
        for (mode in LocalTrackSortOptions) assertTrue(trackSortSection(mode) != 0)
        for (mode in listOf(LibraryTrackSortMode.AlbumArtist, LibraryTrackSortMode.Genre,
            LibraryTrackSortMode.Composer, LibraryTrackSortMode.FileNameDescending)) {
            assertEquals(LibraryTrackSortMode.Title, linkedTrackSort(mode))
        }
    }
}
