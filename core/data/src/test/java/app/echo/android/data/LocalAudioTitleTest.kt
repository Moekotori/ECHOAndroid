package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalAudioTitleTest {
    @Test
    fun missingProviderTitleFallsBackToOriginalFilename() {
        for (title in listOf(null, "", "  ", "<unknown>")) {
            assertEquals("六兆年と一夜物語", localAudioTitle(title, "六兆年と一夜物語.flac"))
            assertEquals("봄날 🎵.Live", localAudioTitle(title, "봄날 🎵.Live.mp3"))
        }
        assertEquals("Song", localAudioTitle(null, "Song"))
        assertEquals(UnknownTrackTitle, localAudioTitle(null, null))
    }

    @Test
    fun originalTagWinsOverFilename() {
        assertEquals("𠮷野家 — Live", localAudioTitle("𠮷野家 — Live", "track01.mp3"))
    }
}
