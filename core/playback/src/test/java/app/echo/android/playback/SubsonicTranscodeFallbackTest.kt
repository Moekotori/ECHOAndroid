package app.echo.android.playback

import org.junit.Assert.*
import org.junit.Test

class SubsonicTranscodeFallbackTest {
    @Test fun retryPreservesPausedIntentAndSeekPosition() {
        val raw = "https://server/rest/stream.view?id=s"
        val paused = requireNotNull(subsonicTranscodeFallback(raw, 42_000, false))
        assertEquals(42_000L, paused.positionMs)
        assertFalse(paused.playWhenReady)
        assertTrue(paused.uri.contains("format=mp3"))
        assertNull(subsonicTranscodeFallback(paused.uri, 42_000, false))
        // A later explicit request for the original song gets its own bounded fallback.
        val replay = requireNotNull(subsonicTranscodeFallback(raw, 0, true))
        assertTrue(replay.playWhenReady)
        assertEquals(0L, replay.positionMs)
    }
}
