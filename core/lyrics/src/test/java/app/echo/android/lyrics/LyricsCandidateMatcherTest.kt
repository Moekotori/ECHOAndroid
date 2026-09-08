package app.echo.android.lyrics

import org.junit.Assert.*
import org.junit.Test

class LyricsCandidateMatcherTest {
    @Test fun partialTitlesAreManualOnlyAndArtistSubstringsAreRejected() {
        val match = requireNotNull(LyricsCandidateMatcher(EchoLyricsSearchRequest("光", "歌手甲", durationMs = 180000))
            .match("光年之外", "歌手甲", null, 180000))
        assertFalse(match.automatic)
        assertNull(LyricsCandidateMatcher(EchoLyricsSearchRequest("Hello", "Ann", durationMs = 180000))
            .match("Hello", "Joanne", null, 180000))
    }

    @Test fun requiresSupportingMetadataAndDoesNotRewardEmptyAlbum() {
        val matcher = LyricsCandidateMatcher(EchoLyricsSearchRequest("Song", "Artist", "Album"))
        assertFalse(requireNotNull(matcher.match("Song", "Artist", "", 0)).automatic)
        assertTrue(requireNotNull(matcher.match("Song", "Artist", "Album", 0)).automatic)
        assertFalse(requireNotNull(matcher.match("Song", "Artist", "Other", 0)).automatic)
    }

    @Test fun normalizesWidthAndCollaborationOrderWithoutSplittingNamesAtSpaces() {
        val matcher = LyricsCandidateMatcher(EchoLyricsSearchRequest("Ｓｏｎｇ", "Alice / Bob", durationMs = 180000))
        assertTrue(requireNotNull(matcher.match("Song", "Bob & Alice", null, 180000)).fast)
        assertNull(LyricsCandidateMatcher(EchoLyricsSearchRequest("Song", "Ann", durationMs = 180000))
            .match("Song", "Mary Ann", null, 180000))
    }

    @Test fun keepsDifferentVersionsOutEvenWithMatchingDuration() {
        val matcher = LyricsCandidateMatcher(EchoLyricsSearchRequest("Song", "Artist", durationMs = 180000))
        listOf("Acoustic", "Demo", "Radio Edit", "TV Size", "Live", "Remix", "Instrumental", "伴奏", "现场")
            .forEach { assertNull(it, matcher.match("Song ($it)", "Artist", null, 180000)) }
        // "live" inside an ordinary word must not be classified as a live recording.
        assertNotNull(LyricsCandidateMatcher(EchoLyricsSearchRequest("Alive", "Artist", durationMs = 180000))
            .match("Alive Again", "Artist", null, 180000))
    }

    @Test fun durationBoundariesAndAlbumConflictsLimitAutomaticSelection() {
        val matcher = LyricsCandidateMatcher(EchoLyricsSearchRequest("Song", "Artist", "Album", 180000))
        assertTrue(requireNotNull(matcher.match("Song", "Artist", "Album", 183000)).fast)
        assertFalse(requireNotNull(matcher.match("Song", "Artist", "Album", 183001)).fast)
        assertTrue(requireNotNull(matcher.match("Song", "Artist", "Album", 188000)).automatic)
        assertFalse(requireNotNull(matcher.match("Song", "Artist", "Album", 188001)).automatic)
        assertFalse(requireNotNull(matcher.match("Song", "Artist", "Other", 184000)).automatic)
        assertNull(matcher.match("Song", "Artist", "Album", 195001))
    }
}
