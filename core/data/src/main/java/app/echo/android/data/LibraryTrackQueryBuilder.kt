package app.echo.android.data

import androidx.sqlite.db.SimpleSQLiteQuery
import app.echo.android.model.library.LibraryTrackSortMode
import app.echo.android.model.library.LibraryTrackFilter

internal object LibraryTrackQueryBuilder {
    // A single query shape owns filtering and ordering for pages, locating and playback queues.
    fun buildTrackPagingSql(
        query: String,
        useFts: Boolean,
        sort: LibraryTrackSortMode,
        idsOnly: Boolean = false,
        filter: LibraryTrackFilter = LibraryTrackFilter(),
    ): String = buildSql(query, useFts, sort, idsOnly, localSources = true, filter = filter)

    fun buildTrackQueueSql(
        query: String,
        useFts: Boolean,
        localSources: Boolean,
        limit: Int,
        sort: LibraryTrackSortMode = LibraryTrackSortMode.Title,
        filter: LibraryTrackFilter = LibraryTrackFilter(),
        offset: Int = 0,
    ): String = buildSql(query, useFts, sort, idsOnly = false, localSources, filter) + windowSql(limit, offset)

    fun boundQuery(
        query: String,
        matchQuery: String?,
        sort: LibraryTrackSortMode,
        localSources: Boolean = true,
        idsOnly: Boolean = false,
        limit: Int? = null,
        filter: LibraryTrackFilter = LibraryTrackFilter(),
        offset: Int = 0,
    ): SimpleSQLiteQuery {
        val normalizedFilter = filter.normalized()
        val filterSql = normalizedFilter.toSql()
        val useFts = matchQuery != null
        val args = mutableListOf<Any>()
        if (query.isNotBlank() && useFts) args += requireNotNull(matchQuery)
        librarySearchTerms(query).forEach { term -> repeat(SearchFields.size) { args += term } }
        args.addAll(filterSql.args)
        if (query.isNotBlank() && sort == LibraryTrackSortMode.Title) {
            repeat(3) { args += query.trim().lowercase() }
        }
        val sql = buildSql(query, useFts, sort, idsOnly, localSources, normalizedFilter, filterSql) +
            windowSql(limit, offset)
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    private fun windowSql(limit: Int?, offset: Int): String {
        require(offset >= 0 && (limit != null || offset == 0))
        return limit?.let { " LIMIT ${it.coerceAtLeast(1)}" + if (offset > 0) " OFFSET $offset" else "" }.orEmpty()
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
        filter: LibraryTrackFilter,
        filterSql: LibraryTrackFilterSql = filter.toSql(),
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
        if (query.isBlank() && !filter.isActive) {
            val index = when (sort) {
                LibraryTrackSortMode.Title, LibraryTrackSortMode.TitleDescending -> "index_library_tracks_title_id"
                LibraryTrackSortMode.Duration, LibraryTrackSortMode.DurationAscending -> "index_library_tracks_durationMs_title_id"
                LibraryTrackSortMode.RecentlyUpdated, LibraryTrackSortMode.ModifiedOldest -> "index_library_tracks_dateModifiedSeconds_title_id"
                else -> null
            }
            index?.let { append(" INDEXED BY $it") }
        }
        if (sort == LibraryTrackSortMode.FrequentlyPlayed || sort == LibraryTrackSortMode.RecentlyPlayed || sort == LibraryTrackSortMode.LeastPlayed) {
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
        append(filterSql.clause)
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
            LibraryTrackSortMode.TitleDescending -> "library_tracks.title COLLATE NOCASE DESC"
            LibraryTrackSortMode.ArtistDescending -> "library_tracks.artist COLLATE NOCASE DESC, library_tracks.album COLLATE NOCASE ASC, library_tracks.discNumber, library_tracks.trackNumber, library_tracks.title COLLATE NOCASE ASC"
            LibraryTrackSortMode.AlbumDescending -> "library_tracks.album COLLATE NOCASE DESC, library_tracks.discNumber, library_tracks.trackNumber, library_tracks.title COLLATE NOCASE ASC"
            LibraryTrackSortMode.DurationAscending -> "library_tracks.durationMs ASC, library_tracks.title COLLATE NOCASE ASC"
            LibraryTrackSortMode.LeastPlayed -> "COALESCE(library_playback_stats.playCount, 0) ASC, library_tracks.title COLLATE NOCASE ASC"
            LibraryTrackSortMode.YearAscending -> "CASE WHEN COALESCE(library_tracks.year, 0) <= 0 THEN 1 ELSE 0 END, library_tracks.year ASC, library_tracks.title COLLATE NOCASE ASC"
            LibraryTrackSortMode.YearDescending -> "CASE WHEN COALESCE(library_tracks.year, 0) <= 0 THEN 1 ELSE 0 END, library_tracks.year DESC, library_tracks.title COLLATE NOCASE ASC"
            LibraryTrackSortMode.FileName -> "COALESCE(NULLIF(library_tracks.fileName, ''), library_tracks.title) COLLATE NOCASE ASC, library_tracks.title COLLATE NOCASE ASC"
            LibraryTrackSortMode.Folder -> "COALESCE(library_tracks.relativePath, '') COLLATE NOCASE ASC, library_tracks.title COLLATE NOCASE ASC"
            LibraryTrackSortMode.SizeAscending -> "library_tracks.sizeBytes ASC, library_tracks.title COLLATE NOCASE ASC"
            LibraryTrackSortMode.SizeDescending -> "library_tracks.sizeBytes DESC, library_tracks.title COLLATE NOCASE ASC"
            LibraryTrackSortMode.SampleRateAscending -> "CASE WHEN COALESCE(library_tracks.sampleRateHz, 0) <= 0 THEN 1 ELSE 0 END, library_tracks.sampleRateHz ASC, library_tracks.title COLLATE NOCASE ASC"
            LibraryTrackSortMode.SampleRateDescending -> "CASE WHEN COALESCE(library_tracks.sampleRateHz, 0) <= 0 THEN 1 ELSE 0 END, library_tracks.sampleRateHz DESC, library_tracks.title COLLATE NOCASE ASC"
            LibraryTrackSortMode.Format -> "$LibraryFileFormatSql ASC, library_tracks.title COLLATE NOCASE ASC"
            LibraryTrackSortMode.ModifiedOldest -> "library_tracks.dateModifiedSeconds ASC, library_tracks.title COLLATE NOCASE ASC"
            LibraryTrackSortMode.TrackNumber -> "COALESCE(library_tracks.discNumber, 0), COALESCE(library_tracks.trackNumber, 0), library_tracks.title COLLATE NOCASE ASC"
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
