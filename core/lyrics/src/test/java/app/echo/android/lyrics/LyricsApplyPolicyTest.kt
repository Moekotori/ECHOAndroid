package app.echo.android.lyrics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsApplyPolicyTest {
    @Test
    fun staleTrackResultIsDropped() {
        assertFalse(
            LyricsApplyPolicy.shouldApplyLyricsResult(
                loadedTrackId = "track-a",
                currentTrackId = "track-b",
            ),
        )
    }

    @Test
    fun currentTrackResultIsApplied() {
        assertTrue(
            LyricsApplyPolicy.shouldApplyLyricsResult(
                loadedTrackId = "track-a",
                currentTrackId = "track-a",
            ),
        )
    }

    @Test
    fun nullCurrentDropsResult() {
        assertFalse(
            LyricsApplyPolicy.shouldApplyLyricsResult(
                loadedTrackId = "track-a",
                currentTrackId = null,
            ),
        )
    }
}
