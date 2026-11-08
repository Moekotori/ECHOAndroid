package app.echo.android.model.playback

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackHistoryFilterTest {
    // 2026-11-08 is a Sunday: the ECHO PC week (Monday first) still runs 11-02 .. 11-08.
    private val sunday = LocalDate.of(2026, 11, 8).toEpochDay()

    private fun bounds(filter: PlaybackHistoryFilter) = filter.dayBounds(sunday)

    @Test fun weekStartsOnMondayEvenWhenTodayIsSunday() {
        val week = bounds(PlaybackHistoryFilter(period = PlaybackHistoryPeriod.Week))
        assertEquals(LocalDate.of(2026, 11, 2).toEpochDay(), week.fromInclusive)
        assertEquals(LocalDate.of(2026, 11, 9).toEpochDay(), week.untilExclusive)
    }

    @Test fun monthAndYearCoverTheCalendarPeriodContainingToday() {
        val month = bounds(PlaybackHistoryFilter(period = PlaybackHistoryPeriod.Month))
        assertEquals(LocalDate.of(2026, 11, 1).toEpochDay(), month.fromInclusive)
        assertEquals(LocalDate.of(2026, 12, 1).toEpochDay(), month.untilExclusive)
        val year = bounds(PlaybackHistoryFilter(period = PlaybackHistoryPeriod.Year))
        assertEquals(LocalDate.of(2026, 1, 1).toEpochDay(), year.fromInclusive)
        assertEquals(LocalDate.of(2027, 1, 1).toEpochDay(), year.untilExclusive)
    }

    @Test fun dayAndCustomAreInclusiveOfTheirLastDay() {
        val from = LocalDate.of(2026, 10, 30).toEpochDay()
        val to = LocalDate.of(2026, 11, 2).toEpochDay()
        assertEquals(
            PlaybackHistoryDayBounds(from, from + 1),
            bounds(PlaybackHistoryFilter(period = PlaybackHistoryPeriod.Day, fromEpochDay = from)),
        )
        assertEquals(
            PlaybackHistoryDayBounds(from, to + 1),
            bounds(PlaybackHistoryFilter(period = PlaybackHistoryPeriod.Custom, fromEpochDay = from, toEpochDay = to)),
        )
    }

    @Test fun incompleteOrReversedRangesDoNotHideHistory() {
        val open = PlaybackHistoryDayBounds(null, null)
        assertEquals(open, bounds(PlaybackHistoryFilter(period = PlaybackHistoryPeriod.Day)))
        assertEquals(open, bounds(PlaybackHistoryFilter(period = PlaybackHistoryPeriod.Custom, fromEpochDay = 10)))
        assertEquals(open, bounds(PlaybackHistoryFilter(period = PlaybackHistoryPeriod.Custom, fromEpochDay = 10, toEpochDay = 9)))
    }

    @Test fun playsSortFallsBackToRecentOnTheTimeline() {
        val timeline = PlaybackHistoryFilter(sort = PlaybackHistorySort.Plays)
        assertEquals(PlaybackHistorySort.Recent, timeline.effectiveSort)
        assertEquals(PlaybackHistorySort.Plays, timeline.copy(grouping = PlaybackHistoryGrouping.Tracks).effectiveSort)
    }

    @Test fun onlyQueryOrPeriodCountAsFilters() {
        assertFalse(PlaybackHistoryFilter(sort = PlaybackHistorySort.Title, ascending = true).isFiltered)
        assertTrue(PlaybackHistoryFilter(query = "x").isFiltered)
        assertTrue(PlaybackHistoryFilter(period = PlaybackHistoryPeriod.Today).isFiltered)
    }
}
