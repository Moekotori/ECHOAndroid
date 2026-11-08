package app.echo.android.data

import androidx.paging.PagingConfig
import androidx.paging.Pager
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState
import app.echo.android.model.library.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.Collections
import org.junit.Assert.*
import org.junit.Test

class LibraryTrackPagingTest {
    @Test fun distantLocateLoadsTheDestinationWithoutWalkingInterveningPages() = runBlocking {
        val config = libraryTrackPagingConfig()
        val offsets = Collections.synchronizedList(mutableListOf<Int>())
        val updates = Channel<Unit>(Channel.CONFLATED)
        val presenter = object : PagingDataPresenter<String>(coroutineContext) {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<String>) {
                updates.trySend(Unit)
            }
        }
        val pager = Pager(config) {
            LibraryTrackPagingSource<String>(readWindow = { offset, limit, _ ->
                offsets.add(offset)
                LibraryTrackWindow((offset until minOf(offset + limit, 10_000)).map(Int::toString),
                    count = 10_000, offset = offset)
            })
        }
        val collection = launch { pager.flow.collectLatest { presenter.collectFrom(it) } }
        try {
            withTimeout(5_000) {
                while (presenter.snapshot().items.isEmpty()) updates.receive()
                presenter[9_000]
                while (presenter.peek(9_000) != "9000") updates.receive()
            }
            assertTrue("Locate should skip the intervening pages: $offsets", offsets.size < 10)
            assertTrue(offsets.any { it in (9_000 - config.initialLoadSize)..9_000 })
            assertTrue(presenter.snapshot().items.size <= config.maxSize)
        } finally {
            collection.cancelAndJoin()
            updates.close()
        }
    }

    @Test fun onlyTablesThatAffectTheCurrentResultInvalidatePaging() {
        val plain = trackPagingObservedTables(false, LibraryTrackSortMode.Title, LibraryTrackFilter())
        assertFalse(plain.contains("library_playback_stats"))
        assertFalse(plain.contains("library_lyrics_index"))
        assertFalse(plain.contains("library_duplicate_members"))
        assertTrue(plain.contains("library_duplicate_control"))
        val filtered = trackPagingObservedTables(true, LibraryTrackSortMode.Title,
            LibraryTrackFilter(played = LibraryPlayedFilter.NeverPlayed, favoritesOnly = true, lyrics = LibraryPresenceFilter.Missing))
        assertTrue(filtered.containsAll(listOf("library_tracks_fts", "library_playback_stats", "library_favorites", "library_lyrics_index")))
        for (mode in listOf(LibraryTrackSortMode.RecentlyPlayed, LibraryTrackSortMode.FrequentlyPlayed, LibraryTrackSortMode.LeastPlayed)) {
            assertTrue(trackPagingObservedTables(false, mode, LibraryTrackFilter()).contains("library_playback_stats"))
        }
    }

    @Test fun windowsKeepAbsoluteKeysPlaceholdersAndCacheCountUntilInvalidation() = runBlocking {
        val rows = (0 until 25).map(Int::toString)
        var counts = 0
        var observations = 0
        val source = LibraryTrackPagingSource<String>(readWindow = { offset, limit, knownCount ->
            if (knownCount == null) counts++
            LibraryTrackWindow(rows.drop(offset).take(limit), rows.size, offset)
        }, observe = { observations++ })
        val refresh = source.load(LoadParams.Refresh(10, 10, true)) as LoadResult.Page
        assertEquals(rows.subList(10, 20), refresh.data)
        assertEquals(10, refresh.prevKey)
        assertEquals(20, refresh.nextKey)
        assertEquals(10, refresh.itemsBefore)
        assertEquals(5, refresh.itemsAfter)
        val previous = source.load(LoadParams.Prepend(refresh.prevKey!!, 15, true)) as LoadResult.Page
        assertEquals(rows.take(10), previous.data)
        assertNull(previous.prevKey)
        val next = source.load(LoadParams.Append(refresh.nextKey!!, 10, true)) as LoadResult.Page
        assertEquals(rows.takeLast(5), next.data)
        assertNull(next.nextKey)
        assertEquals(0, next.itemsAfter)
        assertEquals(1, counts)
        assertEquals(1, observations)
        assertEquals(10, source.getRefreshKey(PagingState(listOf(refresh), 15, PagingConfig(10, initialLoadSize = 10), 10)))
        source.invalidate()
        assertTrue(source.load(LoadParams.Refresh(null, 10, true)) is LoadResult.Invalid)
        assertEquals(1, counts)
    }

    @Test fun invalidationDuringAReadDiscardsTheWindowAndCancellationPropagates() = runBlocking {
        lateinit var source: LibraryTrackPagingSource<String>
        source = LibraryTrackPagingSource(readWindow = { _, _, _ ->
            source.invalidate()
            LibraryTrackWindow(listOf("stale"), 1, 0)
        })
        assertTrue(source.load(LoadParams.Refresh(null, 10, true)) is LoadResult.Invalid)
        val cancelled = LibraryTrackPagingSource<String>(readWindow = { _, _, _ -> throw CancellationException("cancelled") })
        try {
            cancelled.load(LoadParams.Refresh(null, 10, true))
            fail("Cancellation must not become a paging error")
        } catch (_: CancellationException) { }
    }
}
