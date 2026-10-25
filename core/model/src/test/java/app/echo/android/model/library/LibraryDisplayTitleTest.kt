package app.echo.android.model.library

import org.junit.Assert.*
import org.junit.Test

class LibraryDisplayTitleTest {
    private val track = EchoTrack("one", "content://media/one", "Tagged title", "Artist", fileName = "01 - Recording.flac")
    @Test fun fileNameDisplayDoesNotChangeMetadataOrRequireUriAccess() {
        assertEquals("Tagged title", track.libraryDisplayTitle(false))
        assertEquals("01 - Recording.flac", track.libraryDisplayTitle(true))
        assertEquals("Tagged title", track.title)
        assertEquals(track.title, track.copy(fileName = null).libraryDisplayTitle(true))
        assertEquals("01 - Recording.flac · Tagged title", track.copy(clipEndMs = 20000).libraryDisplayTitle(true))
    }
}
