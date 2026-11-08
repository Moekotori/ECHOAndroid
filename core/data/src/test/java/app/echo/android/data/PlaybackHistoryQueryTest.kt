package app.echo.android.data

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteProgram
import app.echo.android.model.playback.PlaybackHistoryFilter
import app.echo.android.model.playback.PlaybackHistoryGrouping
import app.echo.android.model.playback.PlaybackHistoryPeriod
import app.echo.android.model.playback.PlaybackHistorySort
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackHistoryQueryTest {
    private class Recorder : SupportSQLiteProgram {
        val values = mutableListOf<Any?>()
        override fun bindNull(index: Int) { values += null }
        override fun bindLong(index: Int, value: Long) { values += value }
        override fun bindDouble(index: Int, value: Double) { values += value }
        override fun bindString(index: Int, value: String) { values += value }
        override fun bindBlob(index: Int, value: ByteArray) { values += value }
        override fun clearBindings() = values.clear()
        override fun close() = Unit
    }

    private fun SimpleSQLiteQuery.args() = Recorder().also(::bindTo).values
    private val today = 20_000L

    @Test fun searchTextIsBoundAndWildcardsAreEscaped() {
        val query = playbackHistoryQuery(PlaybackHistoryFilter(query = " 100%_a\\b "), today)
        assertFalse("search text must never be inlined", query.sql.contains("100"))
        assertEquals(List(3) { "%100\\%\\_a\\\\b%" }, query.args())
    }

    @Test fun periodFiltersTheLocalEpochDayColumnWithAHalfOpenRange() {
        val query = playbackHistoryQuery(PlaybackHistoryFilter(period = PlaybackHistoryPeriod.Today), today)
        assertTrue(query.sql.contains("e.localEpochDay >= ? AND e.localEpochDay < ?"))
        assertEquals(listOf<Any?>(today, today + 1), query.args())
        assertEquals(0, playbackHistoryQuery(PlaybackHistoryFilter(), today).args().size)
    }

    @Test fun everyBoundPlaceholderHasAnArgumentInEveryMode() {
        for (grouping in PlaybackHistoryGrouping.entries) for (sort in PlaybackHistorySort.entries) for (asc in listOf(false, true)) {
            val filter = PlaybackHistoryFilter("a", PlaybackHistoryPeriod.Month, grouping = grouping, sort = sort, ascending = asc)
            val query = playbackHistoryQuery(filter, today, limit = 10)
            assertEquals("$grouping $sort", query.sql.count { it == '?' }, query.args().size)
            assertTrue(query.sql.trimEnd().endsWith("LIMIT 10"))
            assertTrue(query.sql.contains("canReplay"))
        }
    }

    @Test fun aggregatedViewsGroupByTheirKeyAndSkipEmptyNames() {
        fun sql(grouping: PlaybackHistoryGrouping) = playbackHistoryQuery(PlaybackHistoryFilter(grouping = grouping), today).sql
        assertTrue(sql(PlaybackHistoryGrouping.Tracks).contains("GROUP BY e.trackId"))
        assertTrue(sql(PlaybackHistoryGrouping.Albums).contains("TRIM(e.album) != ''"))
        assertTrue(sql(PlaybackHistoryGrouping.Artists).contains("GROUP BY LOWER(TRIM(e.artist))"))
        assertFalse(sql(PlaybackHistoryGrouping.Timeline).contains("GROUP BY"))
    }

    @Test fun everySortEndsWithATieBreakerSoPagesNeverReshuffle() {
        for (sort in PlaybackHistorySort.entries) {
            val sql = playbackHistoryQuery(PlaybackHistoryFilter(grouping = PlaybackHistoryGrouping.Tracks, sort = sort), today).sql
            assertTrue(sort.name, sql.substringAfter("ORDER BY").trim().endsWith("id DESC"))
        }
        val ascending = playbackHistoryQuery(PlaybackHistoryFilter(ascending = true), today).sql
        assertTrue(ascending.contains("ORDER BY playedAtEpochMs ASC, id ASC"))
    }

    @Test fun timelineCannotSortByPlays() {
        val sql = playbackHistoryQuery(PlaybackHistoryFilter(sort = PlaybackHistorySort.Plays), today).sql
        assertTrue(sql.contains("ORDER BY playedAtEpochMs DESC"))
    }

    @Test fun summaryUsesTheSameFilter() {
        val query = playbackHistorySummaryQuery(PlaybackHistoryFilter(query = "x", period = PlaybackHistoryPeriod.Today), today)
        assertEquals(5, query.args().size)
        assertTrue(query.sql.contains("COUNT(DISTINCT e.trackId)"))
    }

    @Test fun dumpSqlForSqliteSmokeCheck() {
        // Optional: ECHO_HISTORY_SQL_DUMP=<dir> lets a script run each statement against a real SQLite database.
        val folder = System.getenv("ECHO_HISTORY_SQL_DUMP") ?: return
        val directory = File(folder).also { it.mkdirs() }
        for (grouping in PlaybackHistoryGrouping.entries) for (sort in PlaybackHistorySort.entries) {
            val filter = PlaybackHistoryFilter("a", PlaybackHistoryPeriod.Month, grouping = grouping, sort = sort)
            File(directory, "${grouping.name}-${sort.name}.sql").writeText(playbackHistoryQuery(filter, today).sql)
        }
        File(directory, "summary.sql").writeText(playbackHistorySummaryQuery(PlaybackHistoryFilter("a"), today).sql)
    }
}
