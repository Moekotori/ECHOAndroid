package app.echo.android.data

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.launch
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryTrackDaoAndroidTest {
    private lateinit var database: EchoLibraryDatabase
    private lateinit var dao: LibraryTrackDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            EchoLibraryDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.trackDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun statsAndAlbumFallbacksAreComputedFromDatabase() = runBlocking {
        dao.upsertBatchWithFts(
            listOf(
                track(id = "1", title = "Song A", artist = "Artist A", album = "Album A", albumArtist = null),
                track(id = "2", title = "Song B", artist = "Artist A", album = "Album A", albumArtist = null),
                track(id = "3", title = "Song C", artist = "", album = null, albumArtist = null),
            ),
        )
        dao.rebuildLibrarySummaries()

        val stats = dao.observeLibraryStats().first()
        assertEquals(3, stats.trackCount)
        assertEquals(2, stats.albumCount)
        assertEquals(2, stats.artistCount)

        val albums = dao.pageAlbums(query = null, sort = "Title").load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false,
            ),
        ) as PagingSource.LoadResult.Page
        val albumA = albums.data.first { it.title == "Album A" }
        assertEquals("Artist A", albumA.albumArtist)
        assertEquals(2, albumA.trackCount)
    }

    @Test
    fun ftsIsUpdatedAndDeletedWithTracks() = runBlocking {
        dao.upsertBatchWithFts(listOf(track(id = "1", title = "Song A")))
        assertEquals("Song A", dao.getTrackQueueByFts("song*", "%song%", 10).single().title)

        dao.upsertBatchWithFts(listOf(track(id = "1", title = "Renamed Song")))
        assertEquals("Renamed Song", dao.getTrackQueueByFts("renamed*", "%renamed%", 10).single().title)
        assertTrue(dao.getTrackQueueByFts("song*", "%song%", 10).isNotEmpty())

        dao.deleteFtsByTrackIds(listOf("1"))
        assertTrue(dao.getTrackQueueByFts("renamed*", "%renamed%", 10).isEmpty())
    }

    @Test
    fun scanBatchRefreshesOldAndNewCategoriesWithoutFinalization() = runBlocking {
        val old = track("scan", "Song", artist = "Old artist", album = "Old album", albumArtist = null)
            .copy(relativePath = "Music/Old/")
        dao.upsertScanBatch(listOf(old))
        val moved = old.copy(artist = "New artist", album = "New album", relativePath = "Music/New/")
            .withScanMetadata(2L)
        dao.upsertScanBatch(listOf(moved))
        val albums = dao.pageAlbums(null, "Title").load(
            PagingSource.LoadParams.Refresh(null, 10, false),
        ) as PagingSource.LoadResult.Page
        assertEquals(listOf("New album"), albums.data.map { it.title })
        assertEquals(1, dao.observeLibraryStats().first().artistCount)
        val folders = database.openHelper.readableDatabase.query("SELECT folderKey FROM library_folder_summaries")
        folders.use {
            assertTrue(it.moveToFirst())
            assertEquals("Music/New/", it.getString(0))
            assertTrue(!it.moveToNext())
        }
        dao.deleteScanBatch(listOf("scan"))
        assertEquals(0, dao.countTracks())
        assertEquals(0, dao.countAlbumSummaries())
        assertTrue(dao.getTrackQueueByFts("song*", "%song%", 10).isEmpty())
    }

    @Test
    fun cancelledScanTransactionRollsBackTracksAndSummaries() = runBlocking {
        dao.upsertScanBatch(listOf(track("committed", "Committed")))
        val job = launch {
            database.withTransaction {
                dao.upsertScanBatch(listOf(track("cancelled", "Cancelled")))
                throw kotlinx.coroutines.CancellationException("stop scan")
            }
        }
        job.join()
        assertEquals(1, dao.countTracks())
        assertEquals(1, dao.countAlbumSummaries())
        assertTrue(dao.getTrackQueueByFts("cancelled*", "%cancelled%", 10).isEmpty())
    }

    private fun track(
        id: String,
        title: String,
        artist: String = "Artist",
        album: String? = "Album",
        albumArtist: String? = "Album Artist",
    ): LibraryTrackEntity =
        LibraryTrackEntity(
            id = id,
            contentUri = "content://track/$id",
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            artworkUri = null,
            durationMs = 60000L,
            trackNumber = 1,
            discNumber = 1,
            year = 2024,
            mimeType = "audio/flac",
            sizeBytes = 1024L,
            dateModifiedSeconds = 1000L,
        ).withScanMetadata(scanRunId = 1L)
}
