package app.echo.android.data

import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibrarySource
import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class EmbeddedTagWriteSafetyTest {
    @Test fun virtualCueAndClippedTracksCannotWriteTheSharedOriginal() {
        val row = EchoTrack("mediastore:1", "content://media/1", "Title", "Artist").toLibraryTrackEntity()
        assertTrue(row.canWriteEmbeddedTags())
        assertFalse(row.copy(id = "mediastore:1#cue:1").canWriteEmbeddedTags())
        assertFalse(row.copy(clipStartMs = 1).canWriteEmbeddedTags())
        assertFalse(row.copy(clipEndMs = 1000).canWriteEmbeddedTags())
        assertFalse(row.copy(source = LibrarySource.WebDav.id).canWriteEmbeddedTags())
    }

    @Test fun oversizedArtworkStopsReadingAtBudgetPlusOne() {
        val source = ByteArray(100_000)
        val input = ByteArrayInputStream(source)
        assertNull(readArtworkSource(input, 1024))
        assertEquals(1025, source.size - input.available())
        val exact = ByteArray(1024) { it.toByte() }
        assertArrayEquals(exact, readArtworkSource(exact.inputStream(), 1024))
    }
}
