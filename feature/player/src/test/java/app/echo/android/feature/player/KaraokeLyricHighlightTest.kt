package app.echo.android.feature.player

import app.echo.android.model.lyrics.EchoLyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeLyricHighlightTest {
    private fun word(start: Long, end: Long?, text: String) = EchoLyricWord(start, end, text)

    @Test
    fun currentWordEmphasisMeetsSteadyInkAtTheWordBoundaries() {
        assertEquals(0.86f, lyricWordHighlightAlpha(0f, 1f), 0.0001f)
        assertEquals(1f, lyricWordHighlightAlpha(0.5f, 1f), 0.0001f)
        assertEquals(0.86f, lyricWordHighlightAlpha(1f, 1f), 0.0001f)
        assertEquals(lyricWordHighlightAlpha(0.25f, 1f), lyricWordHighlightAlpha(0.75f, 1f), 0.0001f)
    }

    @Test
    fun highlightStrengthRemainsBoundedAndDoesNotMoveTheWipeTiming() {
        assertTrue(lyricWordHighlightAlpha(0.5f, 0.45f) < lyricWordHighlightAlpha(0.5f, 1f))
        assertEquals(1f, lyricWordHighlightAlpha(0.5f, 1.35f), 0.0001f)
        assertEquals(0.86f, lyricWordHighlightAlpha(-1f, 1f), 0.0001f)
        assertEquals(0.86f, lyricWordHighlightAlpha(2f, 1f), 0.0001f)
        assertEquals(0.5f, lyricWordFraction(1000, 2000, 1500), 0f)
    }

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

    @Test
    fun wordShapesPreserveDisplaySpacingWithoutRequiringTimedSpaces() {
        val shapes = lyricWordShapes("  Hello  world ", listOf(word(0, 500, "Hello"), word(500, 1000, "world")))
        assertEquals(2, shapes.size)
        assertEquals(2, shapes[0].first)
        assertEquals(7, shapes[0].endExclusive)
        assertEquals(9, shapes[1].first)
        assertEquals(14, shapes[1].endExclusive)
        assertTrue(shapes[1].glyphs.contentEquals(intArrayOf(9, 10, 11, 12, 13)))
    }

    @Test
    fun timedWhitespaceCanDifferFromDisplayWhitespace() {
        val shapes = lyricWordShapes("你好", listOf(word(0, 500, " 你 "), word(500, 1000, "好 ")))
        assertEquals(2, shapes.size)
        assertEquals(1, shapes[1].first)
        assertEquals(2, shapes[1].endExclusive)
    }

    @Test
    fun mismatchedOrIncompleteTranscriptionDoesNotHighlightWrongGlyphs() {
        assertTrue(lyricWordShapes("你好", listOf(word(0, 500, "你呀"))).isEmpty())
        assertTrue(lyricWordShapes("你好", listOf(word(0, 500, "你"))).isEmpty())
        assertTrue(lyricWordShapes("你", listOf(word(0, 500, "你好"))).isEmpty())
    }

    @Test
    fun combiningMarksStayWithTheirBaseCharacter() {
        val shapes = lyricWordShapes("e\u0301好", listOf(word(0, 1000, "e\u0301好")))
        assertTrue(shapes.single().glyphs.contentEquals(intArrayOf(0, 2)))
    }
}
