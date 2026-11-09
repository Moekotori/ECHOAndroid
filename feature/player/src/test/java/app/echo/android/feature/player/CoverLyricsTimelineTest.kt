package app.echo.android.feature.player

import app.echo.android.model.lyrics.EchoLyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverLyricsTimelineTest {
    @Test
    fun followsPlaybackAndSeeksWithoutDisplayingInstrumentalCues() {
        val timeline = CoverLyricsTimeline(
            listOf(
                EchoLyricLine(-1, text = "Untimed text"),
                EchoLyricLine(1_000, text = "First"),
                EchoLyricLine(2_000, text = " "),
                EchoLyricLine(3_000, endMs = 3_500, text = "Second"),
                EchoLyricLine(5_000, text = "Third"),
            ),
        )

        assertEquals(-1, timeline.activeAt(0))
        assertEquals(listOf(null, "First", "Second"), timeline.rowsAround(timeline.contextAt(0)))
        assertEquals(-1, timeline.activeAt(2_000))
        assertEquals(listOf(null, "First", "Second"), timeline.rowsAround(timeline.contextAt(2_000)))
        assertEquals(listOf("First", "Second", "Third"), timeline.rowsAround(timeline.activeAt(3_000)))
        assertEquals(-1, timeline.activeAt(3_500))
        assertEquals(listOf("Second", "Third", null), timeline.rowsAround(timeline.activeAt(5_000)))
        assertEquals(listOf(null, "First", "Second"), timeline.rowsAround(timeline.activeAt(1_000)))
    }

    @Test
    fun hidesDocumentsWithNoTimedText() {
        assertTrue(CoverLyricsTimeline(listOf(EchoLyricLine(-1, text = "Untimed"), EchoLyricLine(0, text = ""))).isEmpty)
    }
}
