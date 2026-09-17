package app.echo.android.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoLockLyricsPolicyTest {
    @Test
    fun overlayRequiresEnabledPlayingLyricsAndLockedScreen() {
        assertTrue(
            EchoLockLyricsPolicy.shouldShowOverLock(
                enabled = true,
                isPlaying = true,
                hasCurrentLine = true,
                screenInteractive = true,
                keyguardLocked = true,
            ),
        )
        assertFalse(
            EchoLockLyricsPolicy.shouldShowOverLock(
                enabled = true,
                isPlaying = true,
                hasCurrentLine = true,
                screenInteractive = false,
                keyguardLocked = true,
            ),
        )
        assertFalse(
            EchoLockLyricsPolicy.shouldShowOverLock(
                enabled = true,
                isPlaying = true,
                hasCurrentLine = true,
                screenInteractive = true,
                keyguardLocked = false,
            ),
        )
        assertFalse(
            EchoLockLyricsPolicy.shouldShowOverLock(
                enabled = false,
                isPlaying = true,
                hasCurrentLine = true,
                screenInteractive = true,
                keyguardLocked = true,
            ),
        )
    }
}
