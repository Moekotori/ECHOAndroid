package app.echo.android.lyrics

import org.junit.Assert.*
import org.junit.Test

class LyricsSidecarNamesTest {
    @Test fun truncatedDirectoryCannotProveAFallbackIsUnique() {
        val seen = listOf("Song-A.lrc", "Exact.lrc")
        assertTrue(LyricsSidecarNames.matching(listOf("Song A.lrc", "SONG-A.lrc"), seen, complete = false).isEmpty())
        assertEquals(listOf("Exact.lrc"), LyricsSidecarNames.matching(listOf("Exact.lrc"), seen, complete = false))
        assertEquals(listOf("Song-A.lrc"), LyricsSidecarNames.matching(listOf("Song A.lrc"), seen, complete = true))
    }
    @Test fun metadataCannotRedirectLookupToAnotherDirectory() {
        assertTrue(LyricsSidecarNames.candidates(null, "../Other/Song").isEmpty())
        assertTrue(LyricsSidecarNames.candidates(null, "..\\Other\\Song").isEmpty())
    }
    @Test fun keepsFilenameAheadOfTagTitleAndAcceptsUppercaseExtension() {
        val candidates = LyricsSidecarNames.candidates("01. Song.flac", "Title")
        assertEquals(listOf("01. Song.LRC", "Title.lrc"), LyricsSidecarNames.matching(candidates, listOf("Title.lrc", "01. Song.LRC", "other.lrc")))
    }
    @Test fun exactMatchWinsAndAmbiguousFallbackIsRejected() {
        assertEquals(listOf("a-b.lrc"), LyricsSidecarNames.matching(listOf("a-b.lrc"), listOf("ab.lrc", "a-b.lrc")))
        assertTrue(LyricsSidecarNames.matching(listOf("a b.lrc"), listOf("ab.lrc", "a-b.lrc")).isEmpty())
        assertEquals(listOf("a-b.lrc"), LyricsSidecarNames.matching(listOf("a b.lrc"), listOf("a-b.lrc")))
        assertEquals(listOf("Song.lrc"), LyricsSidecarNames.matching(listOf("Song.lrc"), listOf("song.lrc", "Song.lrc")))
        assertTrue(LyricsSidecarNames.matching(listOf("SONG.lrc"), listOf("song.lrc", "Song.lrc")).isEmpty())
    }
}
