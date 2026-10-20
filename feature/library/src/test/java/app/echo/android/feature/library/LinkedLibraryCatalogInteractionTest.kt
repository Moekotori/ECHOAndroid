package app.echo.android.feature.library

import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.library.LibraryTrackSortMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class LinkedLibraryCatalogInteractionTest {
    @Test fun randomPaginationKeepsExistingRowsAndDropsRemovedRows() {
        val order = LinkedLibraryRandomOrder()
        val first = (0 until 40).map(::track)
        val shuffled = order.order(first)
        val appended = order.order(first + (40 until 80).map(::track))
        assertEquals(shuffled, appended.take(first.size))
        assertEquals((0 until 80).map { it.toString() }.toSet(), appended.map { it.id }.toSet())
        val retained = order.order((20 until 60).map(::track))
        assertEquals(appended.filter { it.id!!.toInt() in 20 until 60 }, retained)
        // Same identity must use refreshed metadata rather than a cached track object.
        val edited = retained.map { it.copy(title = "edited") }
        assertEquals(edited, order.order(edited))
    }

    @Test fun queryReplacementCancelsAnInProgressCatalogBuild() = runBlocking {
        val job = Job()
        var conversions = 0
        val cache = LinkedLibrarySearchTextCache(transliterate = {
            conversions++
            job.cancel()
            ""
        })
        try {
            withContext(job) {
                LinkedLibraryCatalog.build(cache, (0 until 2_000).map(::track), emptyList(),
                    query = "song", remoteQuery = "song", sortMode = LibraryTrackSortMode.Title)
            }
            fail("Cancelled search must not publish a catalog")
        } catch (_: CancellationException) {
            assertTrue("Stop before converting the rest of the library", conversions < 10)
        }
    }

    private fun track(index: Int) = EchoRemoteTrack(index.toString(), "song $index", "artist $index",
        "album", null, 1_000)
}
