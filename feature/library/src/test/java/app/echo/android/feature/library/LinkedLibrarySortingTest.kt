package app.echo.android.feature.library

import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.library.AlbumSortMode
import app.echo.android.model.library.LibraryTrackSortMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkedLibrarySortingTest {
    @Test fun unsupportedRemoteSortsFallBackToAnAvailableSelection() {
        for (mode in listOf(LibraryTrackSortMode.RecentlyPlayed, LibraryTrackSortMode.FrequentlyPlayed,
            LibraryTrackSortMode.RecentlyUpdated)) {
            assertFalse(mode in LinkedTrackSortOptions)
            assertEquals(LibraryTrackSortMode.Title, linkedTrackSort(mode))
        }
        assertEquals(LibraryTrackSortMode.Duration, linkedTrackSort(LibraryTrackSortMode.Duration))
        assertEquals(LibraryTrackSortMode.Random, linkedTrackSort(LibraryTrackSortMode.Random))
        assertEquals(AlbumSortMode.Title, linkedAlbumSort(AlbumSortMode.RecentlyAdded))
        assertFalse(AlbumSortMode.Year in LinkedAlbumSortOptions)
    }

    @Test fun supersededSearchStopsBeforeWalkingTheWholeLibrary() = runBlocking {
        lateinit var worker: Job
        var conversions = 0
        val cache = LinkedLibrarySearchTextCache(transliterate = {
            if (++conversions == 8) worker.cancel()
            ""
        })
        val tracks = List(256) { EchoRemoteTrack("$it", "song$it", "artist", null, null, 0) }
        worker = launch {
            LinkedLibraryCatalog.build(cache, tracks, emptyList(), "song", "song", LibraryTrackSortMode.Title)
        }
        worker.join()
        assertTrue(worker.isCancelled)
        assertTrue(conversions < 100)
    }
}
