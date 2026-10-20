package app.echo.android.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedTagVerificationTest {
    private val fields = AudioTagFields("Title", "Artist", null, null, null, null, null, lyrics = "New lyrics")

    @Test
    fun verifiesEveryEditedFieldAndAcceptsExplicitClears() {
        val expected = fields.copy(album = "Album", albumArtist = "Owner", trackNumber = 2,
            discNumber = 1, year = 2026, composer = "Composer", genre = "Genre",
            artworkBytes = byteArrayOf(1, 2, 3), replayGainTrackGainDb = -6f)
        val stale = listOf(expected.copy(album = null), expected.copy(albumArtist = null),
            expected.copy(trackNumber = 3), expected.copy(discNumber = 2), expected.copy(year = 2025),
            expected.copy(composer = null), expected.copy(genre = null), expected.copy(artworkBytes = byteArrayOf(9)),
            expected.copy(replayGainTrackGainDb = null))
        stale.forEach { assertFalse(it.matchesWrittenTags(expected)) }
        assertTrue(expected.matchesWrittenTags(expected))
        assertFalse(expected.matchesWrittenTags(expected.copy(composer = null, composerModified = true)))
        assertFalse(expected.matchesWrittenTags(expected.copy(genre = null, genreModified = true)))
        assertTrue(fields.matchesWrittenTags(fields.copy(composerModified = true, genreModified = true)))
    }

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
