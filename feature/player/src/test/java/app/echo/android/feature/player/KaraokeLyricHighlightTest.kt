package app.echo.android.feature.player

import app.echo.android.model.lyrics.EchoLyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeLyricHighlightTest {
    private fun word(start: Long, end: Long?, text: String) = EchoLyricWord(start, end, text)

    @Test
    fun fractionIsZeroBeforeTheWordAndFullWhenTheEndIsMissing() {
        assertEquals(0f, lyricWordFraction(1_000L, 2_000L, 999L), 0f)
        assertEquals(0.5f, lyricWordFraction(1_000L, 2_000L, 1_500L), 0f)
        assertEquals(1f, lyricWordFraction(1_000L, 2_000L, 2_000L), 0f)
        assertEquals(1f, lyricWordFraction(1_000L, null, 1_000L), 0f)
        assertEquals(1f, lyricWordFraction(1_000L, 1_000L, 1_500L), 0f)
    }

    @Test
    fun planLightsFinishedWordsAndOnlyTheGlyphStillMoving() {
        val words = listOf(word(0L, 1_000L, "你"), word(1_000L, 2_000L, "好"), word(2_000L, 3_000L, "呀"))
        assertEquals(KaraokeHighlightPlan(0, -1, 0f), planKaraokeHighlight(words, 3_000L, -1L))
        assertEquals(KaraokeHighlightPlan(0, 0, 0.25f), planKaraokeHighlight(words, 3_000L, 250L))
        assertEquals(KaraokeHighlightPlan(1, 1, 0.5f), planKaraokeHighlight(words, 3_000L, 1_500L))
        assertEquals(KaraokeHighlightPlan(1, -1, 0f), planKaraokeHighlight(words, 3_000L, 1_000L))
        assertEquals(KaraokeHighlightPlan(3, -1, 0f), planKaraokeHighlight(words, 3_000L, 3_000L))
        assertEquals(KaraokeHighlightPlan(1, 1, 0.5f), planKaraokeHighlight(words, 3_000L, 1_500L))
    }

    @Test
    fun glyphWipeFillsEarlierCharactersFirst() {
        assertEquals(0f, lyricGlyphPart(0f, 2, 0), 0f)
        assertEquals(0.5f, lyricGlyphPart(0.25f, 2, 0), 0f)
        assertEquals(0f, lyricGlyphPart(0.25f, 2, 1), 0f)
        assertEquals(1f, lyricGlyphPart(0.75f, 2, 0), 0f)
        assertEquals(0.5f, lyricGlyphPart(0.75f, 2, 1), 0f)
        assertEquals(1f, lyricGlyphPart(1f, 2, 1), 0f)
    }

    @Test
    fun wordShapesSkipLowSurrogates() {
        val shapes = lyricWordShapes("A\uD83D\uDE00", listOf(word(0L, 10L, "A"), word(10L, 20L, "\uD83D\uDE00")))
        assertEquals(0, shapes[0].first)
        assertEquals(1, shapes[0].endExclusive)
        assertEquals(1, shapes[0].glyphs.size)
        assertEquals(1, shapes[1].first)
        assertEquals(3, shapes[1].endExclusive)
        assertTrue(shapes[1].glyphs.contentEquals(intArrayOf(1)))
    }

    @Test
    fun missingWordEndFallsThroughToTheNextWordThenTheLine() {
        val words = listOf(word(0L, null, "a"), word(500L, null, "b"))
        assertEquals(500L, lyricWordEndMs(words, 0, 2_000L))
        assertEquals(2_000L, lyricWordEndMs(words, 1, 2_000L))
    }
}