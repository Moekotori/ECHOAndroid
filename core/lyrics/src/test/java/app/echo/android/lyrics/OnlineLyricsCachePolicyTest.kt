package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyrics
import org.junit.Assert.*
import org.junit.Test

class OnlineLyricsCachePolicyTest {
    @Test fun replacementDecodedDownloadsAreNotAutomaticallyReused() {
        val request = EchoLyricsSearchRequest("Song", "Artist", null, 1000)
        val damaged = OnlineLyricsCachePolicy.stamp(EchoLyrics(metadata = mapOf("text_decoding_lossy" to "true")), request)
        assertFalse(OnlineLyricsCachePolicy.matches(damaged, request))
        assertTrue(OnlineLyricsCachePolicy.matches(damaged.copy(metadata = damaged.metadata + ("text_decoding_lossy" to "false")), request))
    }
    @Test fun invalidatesOldDownloadsAndChangedTagsWhilePreservingLyrics() {
        val request = EchoLyricsSearchRequest("Song", "Artist", "Album", 180000)
        val original = EchoLyrics(metadata = mapOf("provider" to "LRCLIB"))
        assertFalse(OnlineLyricsCachePolicy.matches(original, request))
        val stamped = OnlineLyricsCachePolicy.stamp(original, request)
        assertTrue(OnlineLyricsCachePolicy.matches(stamped, request))
        assertEquals(original.lines, stamped.lines)
        assertEquals("LRCLIB", stamped.metadata["provider"])
        listOf(request.copy(title = "Other"), request.copy(artist = "Other"),
            request.copy(album = "Other"), request.copy(durationMs = 181000))
            .forEach { assertFalse(OnlineLyricsCachePolicy.matches(stamped, it)) }
    }
}
