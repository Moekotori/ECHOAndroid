package app.echo.android.data

import app.echo.android.model.library.AlbumSummary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

/** Temporary developer smoke test; removed after the live check, never part of CI. */
class AlbumOnlineLiveSmokeTest {
    @Test fun liveLookupAndCachedReopen() = runBlocking {
        val directory = Files.createTempDirectory("echo-online-smoke").toFile()
        try {
            val repository = AlbumOnlineInfoRepository(directory)
            val album = AlbumSummary("smoke", "Random Access Memories", "Daft Punk", "Daft Punk", null, 13, 0, 2013)
            val info = repository.load(album, "zh", false)
            assertNotNull(info)
            println("LIVE: title=${info!!.releaseTitle}, date=${info.date}, credits=${info.credits.size}, wiki=${info.wikipediaUrl}, partial=${info.partial}")
            assertTrue(info.credits.isNotEmpty())
            assertFalse(info.description.isNullOrBlank())
            val reopened = AlbumOnlineInfoRepository(directory).load(album, "zh", false)
            assertTrue(reopened!!.cached)
            assertEquals(info.releaseId, reopened.releaseId)
        } finally { directory.deleteRecursively() }
    }
}
