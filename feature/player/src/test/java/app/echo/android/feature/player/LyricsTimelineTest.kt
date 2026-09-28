package app.echo.android.feature.player

import app.echo.android.model.lyrics.EchoLyricLine
import org.junit.Assert.*
import org.junit.Test

class LyricsTimelineTest {
    @Test fun cachedVocalsRespectEndBoundariesAndBackwardSeeking() {
        val timeline = LyricsTimeline(listOf(
            EchoLyricLine(1000, 5000, "Lead"),
            EchoLyricLine(2000, 3000, "Backing"),
            EchoLyricLine(6000, 7000, "Next"),
        ))
        val overlapping = timeline.activeAt(2200)
        assertEquals(setOf(0, 1), overlapping)
        assertSame(overlapping, timeline.activeAt(2800))
        assertEquals(setOf(0), timeline.activeAt(3000))
        assertEquals(emptySet<Int>(), timeline.activeAt(5000))
        assertEquals(setOf(2), timeline.activeAt(6000))
        assertEquals(setOf(0, 1), timeline.activeAt(2200))
        assertEquals(setOf(0, 1), overlapping)
    }

    @Test fun cachedIntroAndGapExpireAtTheNextRealTimestamp() {
        val timeline = LyricsTimeline(listOf(
            EchoLyricLine(1000, 2000, "First"),
            EchoLyricLine(2000, text = ""),
            EchoLyricLine(6000, 7000, "Next"),
        ))
        assertEquals(emptySet<Int>(), timeline.activeAt(0))
        assertEquals(emptySet<Int>(), timeline.activeAt(999))
        assertEquals(setOf(0), timeline.activeAt(1000))
        assertEquals(emptySet<Int>(), timeline.activeAt(2000))
        assertEquals(emptySet<Int>(), timeline.activeAt(5999))
        assertEquals(0, timeline.contextAt(5999))
        assertEquals(setOf(2), timeline.activeAt(6000))
    }
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

    @Test fun contextRemainsOnPreviousContentThroughGapsAndResetsOnBackwardSeek() {
        val timeline = LyricsTimeline(listOf(
            EchoLyricLine(1000, 2000, "First"),
            EchoLyricLine(2000, text = ""),
            EchoLyricLine(6000, 7000, "Next"),
        ))
        assertEquals(-1, timeline.contextAt(0))
        assertEquals(0, timeline.contextAt(1500))
        assertEquals(emptySet<Int>(), timeline.activeAt(4000))
        assertEquals(0, timeline.contextAt(4000))
        assertEquals(2, timeline.contextAt(8000))
        assertEquals(0, timeline.contextAt(1500))
        assertEquals(-1, timeline.contextAt(0))
    }

    @Test fun missingLineEndUsesNextTimestampForLastWordWipe() {
        val timeline = LyricsTimeline(listOf(
            EchoLyricLine(1000, text = "First"),
            EchoLyricLine(3000, text = "Next"),
        ))
        assertEquals(3000L, timeline.endAt(0))
        assertEquals(0.5f, lyricWordFraction(2000, timeline.endAt(0), 2500), 0f)
        assertNull(timeline.endAt(1))
    }
}
