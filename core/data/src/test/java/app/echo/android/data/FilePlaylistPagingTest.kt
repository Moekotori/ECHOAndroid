package app.echo.android.data

import androidx.paging.PagingConfig
import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FilePlaylistPagingTest {
    private fun rows() = List(240) { index -> LibraryTrackEntity("file:$index", "content://course/$index", "$index", "",
        null, null, null, 1000, null, null, null, "audio/mpeg", 1024, dateModifiedSeconds = 0, source = "file-playlist") }

    @Test fun distantRefreshKeepsAnchorAndPrependHasNoGapWhenLoadSizeChanges() = runBlocking {
        var reads = 0
        val source = FilePlaylistPagingSource({ reads++; rows() })
        val page = source.load(LoadParams.Refresh(120, 80, true)) as LoadResult.Page
        val earlier = source.load(LoadParams.Prepend(checkNotNull(page.prevKey), 20, true)) as LoadResult.Page
        assertEquals((100 until 120).map { "file:$it" }, earlier.data.map { it.id })
        assertEquals(100, earlier.itemsBefore)
        val next = source.load(LoadParams.Append(checkNotNull(page.nextKey), 20, true)) as LoadResult.Page
        assertEquals("file:200", next.data.first().id)
        assertEquals(1, reads)
        assertEquals(130, source.getRefreshKey(PagingState(listOf(page), 170, PagingConfig(80, initialLoadSize = 80), 120)))
    }

    @Test fun invalidationDuringReadRejectsTheStaleSnapshot() = runBlocking {
        lateinit var source: FilePlaylistPagingSource
        source = FilePlaylistPagingSource({ source.invalidate(); rows() })
        assertTrue(source.load(LoadParams.Refresh(null, 80, true)) is LoadResult.Invalid)
    }

    @Test fun readFailureCanBeRetriedAndObserverRegistersOnce() = runBlocking {
        var reads = 0; var observers = 0
        val source = FilePlaylistPagingSource({ if (++reads == 1) error("Temporary read failure") else rows() }, { observers++ })
        assertTrue(source.load(LoadParams.Refresh(null, 80, true)) is LoadResult.Error)
        val page = source.load(LoadParams.Refresh(null, 80, true)) as LoadResult.Page
        assertEquals("file:0", page.data.first().id)
        assertEquals(1, observers)
    }
}
