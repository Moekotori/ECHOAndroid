package app.echo.android.data

import androidx.sqlite.db.SimpleSQLiteQuery

/** Fixed ORDER BY clauses let SQLite use ordering indexes instead of sorting CASE expressions. */
internal object LibrarySummaryPagingQuery {
    fun albums(query: String?, sort: String, remote: Boolean): SimpleSQLiteQuery = build(
        table = "library_album_summaries",
        columns = "albumKey, title, albumArtist, artist, artworkUri, trackCount, durationMs, year, addedAtSeconds",
        filter = "isRemote = ${if (remote) 1 else 0}",
        search = listOf("title", "albumArtist", "artist", "pinyinTitle", "pinyinArtist"),
        query = query,
        order = when (sort) {
            "Artist" -> "albumArtist COLLATE NOCASE ASC, "
            "Year" -> "year DESC, "
            "TrackCount" -> "trackCount DESC, "
            "Duration" -> "durationMs DESC, "
            "RecentlyAdded" -> "addedAtSeconds DESC, "
            else -> ""
        } + "title COLLATE NOCASE ASC",
    )

    fun artists(query: String?, sort: String): SimpleSQLiteQuery = build(
        "library_artist_summaries", "artistKey, name, artworkUri, albumCount, trackCount, durationMs",
        null, listOf("name", "pinyinName"), query, countOrder(sort) + "name COLLATE NOCASE ASC",
    )

    fun genres(query: String?, sort: String): SimpleSQLiteQuery = build(
        "library_genre_summaries", "genreKey, name, artworkUri, albumCount, trackCount, durationMs",
        null, listOf("name", "pinyinName"), query, countOrder(sort) + "name COLLATE NOCASE ASC",
    )

    fun folders(query: String?, sort: String): SimpleSQLiteQuery = build(
        "library_folder_summaries",
        "folderKey, path, artworkUri, trackCount, albumCount, artistCount, durationMs, totalSizeBytes, latestModifiedSeconds",
        null, listOf("path", "folderKey"), query,
        when (sort) {
            "Size" -> "totalSizeBytes DESC, "
            "RecentlyModified" -> "latestModifiedSeconds DESC, "
            else -> countOrder(sort)
        } + "CASE WHEN folderKey = '' THEN 1 ELSE 0 END, path COLLATE NOCASE ASC",
    )

    private fun countOrder(sort: String): String = when (sort) {
        "AlbumCount" -> "albumCount DESC, "
        "TrackCount" -> "trackCount DESC, "
        "Duration" -> "durationMs DESC, "
        else -> ""
    }

    private fun build(
        table: String,
        columns: String,
        filter: String?,
        search: List<String>,
        query: String?,
        order: String,
    ): SimpleSQLiteQuery {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<Any>()
        filter?.let(conditions::add)
        if (query != null) {
            conditions += search.joinToString(" OR ", "(", ")") { column ->
                args += query
                // Keep SQLite lower(), including its ASCII-only case folding, as in the DAO queries.
                if (column.startsWith("pinyin")) "$column LIKE '%' || lower(?) || '%'"
                else "$column LIKE '%' || ? || '%'"
            }
        }
        val where = if (conditions.isEmpty()) "" else conditions.joinToString(" AND ", " WHERE ")
        return SimpleSQLiteQuery("SELECT $columns FROM $table$where ORDER BY $order", args.toTypedArray())
    }
}
