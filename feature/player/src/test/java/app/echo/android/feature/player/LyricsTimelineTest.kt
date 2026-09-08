package app.echo.android.feature.player

import app.echo.android.model.lyrics.EchoLyricLine
import org.junit.Assert.*
import org.junit.Test

class LyricsTimelineTest {
    @Test fun respectsIntroGapsAndOverlappingVocals() {
        val timeline = LyricsTimeline(listOf(
            EchoLyricLine(1000, 4000, "Lead"), EchoLyricLine(2000, 3000, "Backing", isBackground = true),
            EchoLyricLine(6000, 7000, "Next")))
        assertEquals(emptySet<Int>(), timeline.activeAt(0))
        assertEquals(setOf(0, 1), timeline.activeAt(2500))
        assertEquals(setOf(0), timeline.activeAt(3000))
        assertEquals(emptySet<Int>(), timeline.activeAt(5000))
        assertEquals(6000L, timeline.nextStart(5000))
        assertEquals(setOf(2), timeline.activeAt(6000))
        assertEquals(emptySet<Int>(), timeline.activeAt(7000))
    }
    @Test fun emptyTimedLineEndsLrcFocus() {
        val timeline = LyricsTimeline(listOf(EchoLyricLine(1000, text = "A"), EchoLyricLine(2000, text = "")))
        assertEquals(setOf(0), timeline.activeAt(1999))
        assertEquals(emptySet<Int>(), timeline.activeAt(2000))
    }
    @Test fun displayClockReanchorsOnSeekAndStopsOnPause() {
        val clock = LyricsDisplayClock()
        assertEquals(1000L, clock.position(1000, 0, true, 1f))
        assertEquals(1250L, clock.position(1000, 250, true, 1f))
        assertEquals(1650L, clock.position(1000, 2000, true, 1f))
        assertEquals(100L, clock.position(100, 2010, true, 1f))
        assertEquals(100L, clock.position(100, 2200, false, 1f))
    }
}
