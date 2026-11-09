package app.echo.android.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoLockLyricsPolicyTest {
    @Test
    fun windowRequiresEnabledPlayingLyrics() {
        assertTrue(
            EchoLockLyricsPolicy.shouldKeepShowWhenLocked(
                enabled = true,
                isPlaying = true,
                hasCurrentLine = true,
                dismissed = false,
            ),
        )
        assertFalse(
            EchoLockLyricsPolicy.shouldKeepShowWhenLocked(
                enabled = false,
                isPlaying = true,
                hasCurrentLine = true,
                dismissed = false,
            ),
        )
        assertFalse(
            EchoLockLyricsPolicy.shouldKeepShowWhenLocked(
                enabled = true,
                isPlaying = false,
                hasCurrentLine = true,
                dismissed = false,
            ),
        )
        assertFalse(
            EchoLockLyricsPolicy.shouldKeepShowWhenLocked(
                enabled = true,
                isPlaying = true,
                hasCurrentLine = false,
                dismissed = false,
            ),
        )
    }

    @Test
    fun dismissReleasesWindowWhileLyricsAreStillPlaying() {
        assertFalse(
            EchoLockLyricsPolicy.shouldKeepShowWhenLocked(
                enabled = true,
                isPlaying = true,
                hasCurrentLine = true,
                dismissed = true,
            ),
        )
    }
}
