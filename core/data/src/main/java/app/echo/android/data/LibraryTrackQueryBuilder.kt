package app.echo.android.data

import androidx.sqlite.db.SimpleSQLiteQuery
import app.echo.android.model.library.LibraryTrackSortMode

internal object LibraryTrackQueryBuilder {
    // A single query shape owns filtering and ordering for pages, locating and playback queues.
    fun buildTrackPagingSql(
        query: String,
        useFts: Boolean,
        sort: LibraryTrackSortMode,
        idsOnly: Boolean = false,
    ): String = buildSql(query, useFts, sort, idsOnly, localSources = true)

    fun buildTrackQueueSql(
        query: String,
        useFts: Boolean,
        localSources: Boolean,
        limit: Int,
        sort: LibraryTrackSortMode = LibraryTrackSortMode.Title,
    ): String = buildSql(query, useFts, sort, idsOnly = false, localSources) + " LIMIT ${limit.coerceAtLeast(1)}"

    fun boundQuery(
        query: String,
        matchQuery: String?,
        sort: LibraryTrackSortMode,
        localSources: Boolean = true,
        idsOnly: Boolean = false,
        limit: Int? = null,
    ): SimpleSQLiteQuery {
        val useFts = matchQuery != null
        val args = mutableListOf<Any>()
        if (query.isNotBlank() && useFts) args += requireNotNull(matchQuery)
        librarySearchTerms(query).forEach { term -> repeat(SearchFields.size) { args += term } }
        if (query.isNotBlank() && sort == LibraryTrackSortMode.Title) {
            repeat(3) { args += query.trim().lowercase() }
        }
        val sql = buildSql(query, useFts, sort, idsOnly, localSources) +
            (limit?.let { " LIMIT ${it.coerceAtLeast(1)}" } ?: "")
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    private val SearchFields = listOf(
        "COALESCE(library_tracks.normalizedTitle, lower(library_tracks.title))",
        "COALESCE(library_tracks.normalizedArtist, lower(library_tracks.artist))",
        "COALESCE(library_tracks.normalizedAlbum, lower(library_tracks.album), '')",
        "COALESCE(library_tracks.normalizedAlbumArtist, lower(library_tracks.albumArtist), '')",
        "COALESCE(library_tracks.pinyinTitle, '')",
        "COALESCE(library_tracks.pinyinArtist, '')",
        "COALESCE(library_tracks.pinyinAlbum, '')",
    )

    private fun buildSql(
        query: String,
        useFts: Boolean,
        sort: LibraryTrackSortMode,
        idsOnly: Boolean,
        localSources: Boolean,
    ): String = buildString {
        append(if (idsOnly) "SELECT library_tracks.id" else "SELECT library_tracks.*")
        // CROSS JOIN keeps the inverted index as the outer loop. Otherwise SQLite may
        // prefer the source index and evaluate the FTS match once per library row.
        if (query.isNotBlank() && useFts) {
            append(" FROM library_tracks_fts CROSS JOIN library_tracks")
            append(" ON library_tracks.id = library_tracks_fts.trackId")
        } else {
            append(" FROM library_tracks")
        }
        // Walk matching order indexes for unfiltered pages, instead of sorting the entire library.
        if (query.isBlank()) {
            val index = when (sort) {
                LibraryTrackSortMode.Title -> "index_library_tracks_title_id"
                LibraryTrackSortMode.Duration -> "index_library_tracks_durationMs_title_id"
                LibraryTrackSortMode.RecentlyUpdated -> "index_library_tracks_dateModifiedSeconds_title_id"
                else -> null
            }
            index?.let { append(" INDEXED BY $it") }
        }
        if (sort == LibraryTrackSortMode.FrequentlyPlayed || sort == LibraryTrackSortMode.RecentlyPlayed) {
            append(" LEFT JOIN library_playback_stats ON library_tracks.id = library_playback_stats.trackId")
        }
        append(" WHERE ")
        append(if (localSources) LibraryScanPolicy.LocalSourceSql else LibraryScanPolicy.RemoteSourceSql)
        append(" AND library_tracks.id NOT IN (SELECT trackId FROM library_hidden_tracks)")
        if (query.isNotBlank() && useFts) append(" AND library_tracks_fts MATCH ?")
        librarySearchTerms(query).forEach {
            append(" AND ")
            append(SearchFields.joinToString(" OR ", "(", ")") { field -> "instr($field, ?) > 0" })
        }
        append(" ORDER BY ")
        if (query.isNotBlank() && sort == LibraryTrackSortMode.Title) {
            append("CASE WHEN instr(${SearchFields[0]}, ?) > 0 THEN 0 ")
            append("WHEN instr(${SearchFields[1]}, ?) > 0 THEN 1 ")
            append("WHEN instr(${SearchFields[2]}, ?) > 0 THEN 2 ELSE 3 END, ")
        }
        append(trackSortOrder(sort))
        append(", library_tracks.id ASC")
    }

    fun usesFtsMatchWithoutLeadingWildcardOr(sql: String): Boolean =
        sql.contains("MATCH ?") && !sql.contains(" LIKE ")

    private fun trackSortOrder(sort: LibraryTrackSortMode): String =
        when (sort) {
            LibraryTrackSortMode.Title -> "library_tracks.title COLLATE NOCASE ASC"
            LibraryTrackSortMode.Duration -> "library_tracks.durationMs DESC, library_tracks.title COLLATE NOCASE ASC"
            LibraryTrackSortMode.FrequentlyPlayed -> """
                COALESCE(library_playback_stats.playCount, 0) DESC,
                COALESCE(library_playback_stats.lastPlayedAtEpochMs, 0) DESC,
                library_tracks.title COLLATE NOCASE ASC
            """.trimIndent()
            LibraryTrackSortMode.RecentlyPlayed -> """
                COALESCE(library_playback_stats.lastPlayedAtEpochMs, 0) DESC,
                library_tracks.title COLLATE NOCASE ASC
            """.trimIndent()
            LibraryTrackSortMode.Random ->
                "(length(library_tracks.id) * 31 + length(library_tracks.title) * 17 + COALESCE(library_tracks.durationMs, 0)) % 997, library_tracks.id"
            LibraryTrackSortMode.Artist -> """
                CASE WHEN trim(library_tracks.artist) = '' THEN 1 ELSE 0 END ASC,
                library_tracks.artist COLLATE NOCASE ASC,
                CASE WHEN library_tracks.album IS NULL OR trim(library_tracks.album) = '' THEN 1 ELSE 0 END ASC,
                library_tracks.album COLLATE NOCASE ASC,
                CASE WHEN library_tracks.discNumber IS NULL THEN 0 ELSE library_tracks.discNumber END ASC,
                CASE WHEN library_tracks.trackNumber IS NULL THEN 0 ELSE library_tracks.trackNumber END ASC,
                library_tracks.title COLLATE NOCASE ASC
            """.trimIndent()
            LibraryTrackSortMode.Album -> """
                CASE WHEN library_tracks.album IS NULL OR trim(library_tracks.album) = '' THEN 1 ELSE 0 END ASC,
                library_tracks.album COLLATE NOCASE ASC,
                CASE WHEN library_tracks.discNumber IS NULL THEN 0 ELSE library_tracks.discNumber END ASC,
                CASE WHEN library_tracks.trackNumber IS NULL THEN 0 ELSE library_tracks.trackNumber END ASC,
                library_tracks.title COLLATE NOCASE ASC
            """.trimIndent()
            LibraryTrackSortMode.RecentlyUpdated -> """
                library_tracks.dateModifiedSeconds DESC,
                library_tracks.title COLLATE NOCASE ASC
            """.trimIndent()
        }
}
