package app.echo.android.data

import androidx.sqlite.db.SimpleSQLiteQuery
import app.echo.android.model.playback.PlaybackHistoryFilter
import app.echo.android.model.playback.PlaybackHistoryGrouping
import app.echo.android.model.playback.PlaybackHistorySort

private class HistoryWhere(val sql: String, val args: List<Any>)

/** Bind search text and escape LIKE wildcards so a literal percent is searchable. */
private fun historyWhere(filter: PlaybackHistoryFilter, todayEpochDay: Long): HistoryWhere {
    val clauses = mutableListOf<String>()
    val args = mutableListOf<Any>()
    val bounds = filter.dayBounds(todayEpochDay)
    bounds.fromInclusive?.let { clauses += "e.localEpochDay >= ?"; args += it }
    bounds.untilExclusive?.let { clauses += "e.localEpochDay < ?"; args += it }
    val text = filter.query.trim()
    if (text.isNotEmpty()) {
        clauses += """(e.title LIKE ? ESCAPE '\' OR e.artist LIKE ? ESCAPE '\' OR e.album LIKE ? ESCAPE '\')"""
        val pattern = "%${text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
        repeat(3) { args += pattern }
    }
    return HistoryWhere(if (clauses.isEmpty()) "" else clauses.joinToString(" AND ", prefix = "WHERE "), args)
}

private const val CanReplaySql = """EXISTS(SELECT 1 FROM visible_library_tracks t
    WHERE t.id = e.trackId AND TRIM(t.contentUri) != '') AS canReplay"""

/** Stable total order: every sort ends with the newest event so paging never reshuffles ties. */
private fun historyOrder(filter: PlaybackHistoryFilter): String {
    val dir = if (filter.ascending) "ASC" else "DESC"
    return when (filter.effectiveSort) {
        PlaybackHistorySort.Recent -> "playedAtEpochMs $dir, id $dir"
        PlaybackHistorySort.Plays -> "plays $dir, playedAtEpochMs DESC, id DESC"
        PlaybackHistorySort.Time -> "listenedMs $dir, playedAtEpochMs DESC, id DESC"
        PlaybackHistorySort.Title -> "title COLLATE NOCASE $dir, playedAtEpochMs DESC, id DESC"
        PlaybackHistorySort.Artist ->
            "artist COLLATE NOCASE $dir, title COLLATE NOCASE ASC, playedAtEpochMs DESC, id DESC"
    }
}

/**
 * Grouped rows keep the columns of [LibraryPlayEventEntity] so one row type serves every mode.
 * Bare columns come from the newest event because `MAX(playedAtEpochMs)` is the only min/max aggregate.
 */
internal fun playbackHistoryQuery(
    filter: PlaybackHistoryFilter,
    todayEpochDay: Long,
    limit: Int? = null,
): SimpleSQLiteQuery {
    val where = historyWhere(filter, todayEpochDay)
    val order = historyOrder(filter)
    val limitSql = limit?.let { " LIMIT $it" }.orEmpty()
    val grouped = when (filter.grouping) {
        PlaybackHistoryGrouping.Timeline -> null
        PlaybackHistoryGrouping.Tracks -> GroupSql("e.trackId", "e.title", "e.artist", "e.album", "")
        PlaybackHistoryGrouping.Albums -> GroupSql(
            "LOWER(TRIM(e.album))", "e.album", "e.artist", "e.album",
            "e.album IS NOT NULL AND TRIM(e.album) != ''",
        )
        PlaybackHistoryGrouping.Artists -> GroupSql(
            "LOWER(TRIM(e.artist))", "e.artist", "''", "NULL", "TRIM(e.artist) != ''",
        )
    }
    if (grouped == null) {
        return SimpleSQLiteQuery(
            "SELECT e.*, 1 AS plays, $CanReplaySql FROM library_play_events e ${where.sql} ORDER BY $order$limitSql",
            where.args.toTypedArray(),
        )
    }
    val filterSql = when {
        grouped.requirement.isEmpty() -> where.sql
        where.sql.isEmpty() -> "WHERE ${grouped.requirement}"
        else -> "${where.sql} AND ${grouped.requirement}"
    }
    return SimpleSQLiteQuery(
        """
        SELECT e.id, e.trackId, ${grouped.title} AS title, ${grouped.artist} AS artist, ${grouped.album} AS album,
               e.artworkUri, e.source, e.durationMs, SUM(e.listenedMs) AS listenedMs,
               MAX(e.playedAtEpochMs) AS playedAtEpochMs, e.localEpochDay, e.localHour,
               COUNT(*) AS plays, $CanReplaySql
        FROM library_play_events e $filterSql
        GROUP BY ${grouped.key}
        ORDER BY $order$limitSql
        """.trimIndent(),
        where.args.toTypedArray(),
    )
}

private class GroupSql(val key: String, val title: String, val artist: String, val album: String, val requirement: String)

internal fun playbackHistorySummaryQuery(filter: PlaybackHistoryFilter, todayEpochDay: Long): SimpleSQLiteQuery {
    val where = historyWhere(filter, todayEpochDay)
    return SimpleSQLiteQuery(
        """
        SELECT COUNT(*) AS plays, COALESCE(SUM(e.listenedMs), 0) AS listenedMs,
               COUNT(DISTINCT e.trackId) AS tracks, MAX(e.playedAtEpochMs) AS latestPlayedAtEpochMs
        FROM library_play_events e ${where.sql}
        """.trimIndent(),
        where.args.toTypedArray(),
    )
}
