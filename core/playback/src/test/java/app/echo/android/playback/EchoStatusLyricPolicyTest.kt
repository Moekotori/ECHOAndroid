package app.echo.android.playback

import app.echo.android.model.lyrics.EchoLyricDisplaySnapshot
import app.echo.android.model.lyrics.EchoLyricLine
import org.junit.Assert.*
import org.junit.Test

class EchoStatusLyricPolicyTest {
    @Test fun statusDestinationsShareTranslationAndPausePolicy() {
        val playing = EchoLyricDisplaySnapshot(isPlaying = true,
            current = EchoLyricLine(1000, text = "Original", translation = "Translation"))
        assertEquals("Original", EchoStatusLyricPolicy.text(playing, true))
        assertEquals("Original · Translation", EchoStatusLyricPolicy.text(playing, false))
        assertNull(EchoStatusLyricPolicy.text(playing.copy(isPlaying = false), false))
        assertNull(EchoStatusLyricPolicy.text(playing.copy(current = null), false))
        assertTrue(EchoStatusLyricPolicy.supportsSystemStatusBar("Meizu"))
        assertFalse(EchoStatusLyricPolicy.supportsSystemStatusBar("Google"))
    }
}
