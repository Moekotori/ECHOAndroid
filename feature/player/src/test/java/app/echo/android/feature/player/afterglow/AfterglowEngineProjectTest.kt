package app.echo.android.feature.player.afterglow

import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyricWord
import app.echo.android.model.lyrics.EchoLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AfterglowEngineProjectTest {
    private fun plan(vararg lines: EchoLyricLine) = AfterglowPlan.build(EchoLyrics(lines.toList()), 10_000)!!

    @Test fun wordsAreCountedInCodePointsWithoutWhitespace() {
        val words = listOf(EchoLyricWord(1000, 1500, "a "), EchoLyricWord(1500, 2000, "😀"), EchoLyricWord(2000, null, "bc"))
        val spans = AfterglowEngineProject.wordIntervals("a 😀 bc", 1.0, 3.0, words, 100)
        assertEquals(listOf(0 to 1, 1 to 2, 2 to 4), spans.map { it.from to it.to })
        assertEquals(3.0, spans.last().end, 0.0)
        // A word that is not in the line in order is skipped rather than shifting the rest.
        assertEquals(1, AfterglowEngineProject.wordIntervals("ab", 0.0, 2.0,
            listOf(EchoLyricWord(0, 500, "x"), EchoLyricWord(500, 1000, "ab")), 100).size)
    }

    @Test fun projectCarriesTextTimesAndLook() {
        val json = AfterglowEngineProject.build(
            plan(EchoLyricLine(1000, text = "he\"llo\nworld"), EchoLyricLine(3000, text = "bye")),
            10_000, "T", "A", 5L, "night", wordHighlight = false, estimatedHighlight = false)!!
        assertTrue(json.contains("\"text\":\"he\\\"llo world\""))
        assertTrue(json.contains("\"start\":1.0,\"end\":3.0"))
        assertTrue(json.contains("\"style\":\"echo-night\""))
        assertTrue(!json.contains("timings"))
    }

    @Test fun oversizedDocumentsStayOnTheNativeStage() {
        val many = List(513) { EchoLyricLine(it * 100L, text = "line $it") }
        assertNull(AfterglowEngineProject.build(AfterglowPlan.build(EchoLyrics(many), 100_000)!!, 100_000, "", "", 1L, "mist", false, false))
        assertNotNull(AfterglowEngineProject.build(plan(EchoLyricLine(0, text = "x")), 1000, "", "", 1L, "mist", false, false))
    }

    @Test fun quoteIsSafeInsideAJavaScriptLiteral() {
        assertEquals("\"a\\u2028b\\u000a\\\\\"", AfterglowEngineProject.quote("a b\n\\"))
    }
}
