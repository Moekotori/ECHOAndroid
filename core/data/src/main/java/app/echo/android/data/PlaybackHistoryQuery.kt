package app.echo.android.data

import androidx.sqlite.db.SimpleSQLiteQuery

/** Bind search text and escape LIKE wildcards so a literal percent is searchable. */
internal fun playbackHistoryQuery(query: String, fromEpochMs: Long): SimpleSQLiteQuery {
    val text = query.trim()
    val search = if (text.isEmpty()) "" else """
        AND (e.title LIKE ? ESCAPE '\' OR e.artist LIKE ? ESCAPE '\'
             OR e.album LIKE ? ESCAPE '\')
    """.trimIndent()
    val args = mutableListOf<Any>(fromEpochMs)
    if (text.isNotEmpty()) {
        val pattern = "%${text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
        repeat(3) { args.add(pattern) }
    }
    return SimpleSQLiteQuery("""
        SELECT e.*, EXISTS(SELECT 1 FROM library_tracks t
            WHERE t.id = e.trackId AND TRIM(t.contentUri) != '') AS canReplay
        FROM library_play_events e
        WHERE e.playedAtEpochMs >= ? $search
        ORDER BY e.playedAtEpochMs DESC, e.id DESC
    """.trimIndent(), args.toTypedArray())
}
