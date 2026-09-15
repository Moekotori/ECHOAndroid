package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsLineAtPositionTest {
    @Test
    fun introGapHasNoPrimaryLine() {
        val lines = listOf(
            EchoLyricLine(1000, text = "A"),
            EchoLyricLine(2000, text = "B"),
        )
        assertEquals(-1, LyricsLineAtPosition.lastStartedIndex(lines, 0))
        assertNull(LyricsLineAtPosition.primaryLine(lines, 0))
        assertEquals(1000L, LyricsLineAtPosition.nextStartMs(lines, 0))
    }

    @Test
    fun blankTimedLineClearsThePrimaryText() {
        val lyrics = EchoLyrics(
            lines = listOf(
                EchoLyricLine(1000, text = "A"),
                EchoLyricLine(2000, text = "  "),
                EchoLyricLine(3000, text = "B"),
            ),
        )
        assertEquals("A", LyricsLineAtPosition.primaryText(lyrics, 1999))
        assertNull(LyricsLineAtPosition.primaryText(lyrics, 2000))
        assertEquals("B", LyricsLineAtPosition.primaryText(lyrics, 3000))
        assertEquals(3000L, LyricsLineAtPosition.nextStartMs(lyrics.lines, 2000))
    }

    @Test
    fun unsyncedLyricsDoNotBecomeNotificationLines() {
        val lyrics = EchoLyrics(
            lines = listOf(EchoLyricLine(-1, text = "plain")),
        )
        assertTrue(LyricsLineAtPosition.notificationLines(lyrics).isEmpty())
    }

    @Test
    fun notificationLinesSkipBlankAndKeepTimes() {
        val lyrics = EchoLyrics(
            lines = listOf(
                EchoLyricLine(0, text = "One"),
                EchoLyricLine(1500, text = " "),
                EchoLyricLine(3000, text = "Two"),
            ),
        )
        assertEquals(listOf(0L to "One", 3000L to "Two"), LyricsLineAtPosition.notificationLines(lyrics))
    }
}
