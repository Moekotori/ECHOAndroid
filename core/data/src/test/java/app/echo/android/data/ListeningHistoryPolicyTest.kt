package app.echo.android.data

import app.echo.android.model.playback.ListeningStatsRange
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningHistoryPolicyTest {
    @Test
    fun normalTrackNeedsThirtySeconds() {
        assertFalse(ListeningHistoryPolicy.countsAsListen(durationMs = 240_000L, listenedMs = 29_999L))
        assertTrue(ListeningHistoryPolicy.countsAsListen(durationMs = 240_000L, listenedMs = 30_000L))
    }

    @Test
    fun shortTrackCountsAfterHalf() {
        assertFalse(ListeningHistoryPolicy.countsAsListen(durationMs = 20_000L, listenedMs = 9_000L))
        assertTrue(ListeningHistoryPolicy.countsAsListen(durationMs = 20_000L, listenedMs = 10_000L))
    }

    @Test
    fun unknownDurationUsesThirtySeconds() {
        assertFalse(ListeningHistoryPolicy.countsAsListen(durationMs = 0L, listenedMs = 10_000L))
        assertTrue(ListeningHistoryPolicy.countsAsListen(durationMs = 0L, listenedMs = 31_000L))
    }

    @Test
    fun nothingListenedNeverCounts() {
        assertFalse(ListeningHistoryPolicy.countsAsListen(durationMs = 10_000L, listenedMs = 0L))
    }

    @Test
    fun weekStartsAtLocalMidnightSixDaysAgo() {
        val zone = ZoneId.of("Asia/Hong_Kong")
        val now = LocalDate.of(2026, 9, 28).atTime(15, 30).atZone(zone).toInstant().toEpochMilli()
        val expected = LocalDate.of(2026, 9, 22).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected, ListeningHistoryPolicy.rangeStartEpochMs(ListeningStatsRange.Week, now, zone))
    }

    @Test
    fun allTimeStartsAtEpoch() {
        assertEquals(0L, ListeningHistoryPolicy.rangeStartEpochMs(ListeningStatsRange.All, 123_456L, ZoneId.of("UTC")))
    }
}
