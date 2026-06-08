package app.echo.android.data

import androidx.paging.PagingSource
import androidx.room.Room
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
