package app.echo.android.feature.player

import app.echo.android.model.settings.EchoLyricsWordHighlightStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeWordStyleMathTest {
    @Test
    fun estimatedEnvelopeStartsEarlyAndSettlesWithoutTrackingPosition() {
        assertEquals(0f, lyricEstimatedEnvelope(startMs = 1000, now = 899), 0f)
        assertEquals(1f - 0.5f * 0.5f * 0.5f, lyricEstimatedEnvelope(1000, 975), 1e-4f)
        assertEquals(1f, lyricEstimatedEnvelope(1000, 1051), 0f)
    }

    @Test
    fun focusPeaksAtSungWordAndFadesWithDistance() {
        val head = lyricWordFocusHead(completedCount = 2, partialWord = 2, partialProgress = 0.5f)
        assertEquals(2.5f, head, 0f)
        assertEquals(1f, lyricWordFocusWeight(2, head), 0f)
        assertTrue(lyricWordFocusWeight(3, head) < lyricWordFocusWeight(2, head))
        assertEquals(0f, lyricWordFocusWeight(9, head), 0f)
        // Between words the focus rests on the next unsung word.
        assertEquals(3f, lyricWordFocusHead(completedCount = 3, partialWord = -1, partialProgress = 0f), 0f)
    }

    @Test
    fun bumpIsZeroAtWordEdgesAndPeaksInTheMiddle() {
        assertEquals(0f, lyricWordBump(0f), 1e-6f)
        assertEquals(1f, lyricWordBump(0.5f), 1e-6f)
        assertEquals(0f, lyricWordBump(1f), 1e-5f)
    }

    @Test
    fun lightPresetFallsBackToFadeAndUnknownIdsToSweep() {
        assertEquals(EchoLyricsWordHighlightStyle.Fade,
            resolveLyricWordHighlightStyle(EchoLyricsWordHighlightStyle.Wave, animated = false))
        assertEquals(EchoLyricsWordHighlightStyle.Sweep,
            resolveLyricWordHighlightStyle(EchoLyricsWordHighlightStyle.Sweep, animated = false))
        assertEquals(EchoLyricsWordHighlightStyle.Wave,
            resolveLyricWordHighlightStyle(EchoLyricsWordHighlightStyle.Wave, animated = true))
        assertEquals(EchoLyricsWordHighlightStyle.Sweep, EchoLyricsWordHighlightStyle.fromId("nope"))
        assertEquals(EchoLyricsWordHighlightStyle.Dots, EchoLyricsWordHighlightStyle.fromId("dots"))
    }
}
