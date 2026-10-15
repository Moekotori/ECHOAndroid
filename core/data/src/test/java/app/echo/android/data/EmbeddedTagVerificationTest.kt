package app.echo.android.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedTagVerificationTest {
    private val fields = AudioTagFields("Title", "Artist", null, null, null, null, null, lyrics = "New lyrics")

    @Test
    fun rejectsStaleOrMissingLyricsDespiteMatchingTitleAndArtist() {
        assertFalse(fields.copy(lyrics = "Old lyrics").matchesWrittenTags(fields))
        assertFalse(fields.copy(lyrics = null).matchesWrittenTags(fields))
        assertFalse((null as AudioTagFields?).matchesWrittenTags(fields))
        assertTrue(fields.matchesWrittenTags(fields))
    }

    @Test
    fun explicitClearMustRemoveLyricsButUnchangedRequestPreservesThem() {
        assertFalse(fields.matchesWrittenTags(fields.copy(lyrics = "")))
        assertTrue(fields.copy(lyrics = null).matchesWrittenTags(fields.copy(lyrics = "")))
        assertTrue(fields.matchesWrittenTags(fields.copy(lyrics = null)))
    }

    @Test
    fun acceptsReaderNormalizationWithoutIgnoringContentChanges() {
        assertTrue(fields.copy(lyrics = "First\nSecond").matchesWrittenTags(fields.copy(lyrics = " First\r\nSecond\n")))
        assertFalse(fields.copy(lyrics = "First\nChanged").matchesWrittenTags(fields.copy(lyrics = "First\nSecond")))
    }
}
