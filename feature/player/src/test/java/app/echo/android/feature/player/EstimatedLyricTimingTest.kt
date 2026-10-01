package app.echo.android.feature.player

import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyricWord
import org.junit.Assert.*
import org.junit.Test

class EstimatedLyricTimingTest {
    @Test fun preservesRealTimingAndDoesNotEstimateUnsyncedOrUnknownEnds() {
        val words = listOf(EchoLyricWord(1200, 1700, "你"))
        assertSame(words, estimatedLyricWords(EchoLyricLine(1000, text = "你", words = words), 3000))
        assertTrue(estimatedLyricWords(EchoLyricLine(-1, text = "你好"), 3000).isEmpty())
        assertTrue(estimatedLyricWords(EchoLyricLine(1000, text = "你好"), null).isEmpty())
        assertTrue(estimatedLyricWords(EchoLyricLine(1000, text = "你好"), 1000).isEmpty())
    }

    @Test fun keepsGraphemesAndSpacingAndFitsInsideTheLine() {
        val line = EchoLyricLine(1000, 3000, "你 e\u0301\uD83D\uDE00，好")
        val words = estimatedLyricWords(line, 9000)
        assertEquals(line.text, words.joinToString("") { it.text })
        assertTrue(words.any { it.text == "e\u0301" })
        assertTrue(words.any { it.text == "\uD83D\uDE00" })
        assertEquals(1000L, words.first().startMs)
        assertTrue(words.last().endMs!! <= 3000)
        assertTrue(words.zipWithNext().all { (a, b) -> a.endMs == b.startMs })
        assertEquals(words.size, lyricWordShapes(line.text, words).size)
    }

    @Test fun longInstrumentalGapsDoNotStretchAShortSentence() {
        val line = EchoLyricLine(1000, text = "你好")
        val words = estimatedLyricWords(line, 60_000)
        assertTrue(words.last().endMs!! < 5000)
        assertEquals(1000L, words.first().startMs)
        assertEquals(KaraokeHighlightPlan(words.size, -1, 0f), planKaraokeHighlight(words, 60_000, 5000))
    }
}
