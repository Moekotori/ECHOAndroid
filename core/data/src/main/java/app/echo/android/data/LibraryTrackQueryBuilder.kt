package app.echo.android.data

import app.echo.android.model.library.LibraryTrackSortMode

internal object LibraryTrackQueryBuilder {
    fun buildTrackPagingSql(
        query: String,
        useFts: Boolean,
        sort: LibraryTrackSortMode,
    ): String {
        val sql = StringBuilder()
        sql.append("SELECT library_tracks.* FROM library_tracks")
        if (
            sort == LibraryTrackSortMode.FrequentlyPlayed ||
            sort == LibraryTrackSortMode.RecentlyPlayed
        ) {
            sql.appendLine()
            sql.append(
                """
                LEFT JOIN library_playback_stats
                    ON library_tracks.id = library_playback_stats.trackId
                """.trimIndent(),
            )
        }
        val trimmed = query.trim()
        if (trimmed.isNotBlank() && useFts) {
            sql.appendLine()
            sql.append("JOIN library_tracks_fts ON library_tracks.id = library_tracks_fts.trackId")
            sql.appendLine()
            sql.append("WHERE library_tracks_fts MATCH ?")
            sql.append(" AND ")
            sql.append(LibraryScanPolicy.LocalSourceSql)
        } else if (trimmed.isNotBlank()) {
            sql.appendLine()
            sql.append(
                """
                WHERE (library_tracks.normalizedTitle LIKE ?
                   OR library_tracks.normalizedArtist LIKE ?
                   OR library_tracks.normalizedAlbum LIKE ?
                   OR library_tracks.pinyinTitle LIKE ?
                   OR library_tracks.pinyinArtist LIKE ?
                   OR library_tracks.pinyinAlbum LIKE ?)
                """.trimIndent(),
            )
            sql.append(" AND ")
            sql.append(LibraryScanPolicy.LocalSourceSql)
        } else {
            sql.appendLine()
            sql.append("WHERE ")
            sql.append(LibraryScanPolicy.LocalSourceSql)
        }
        sql.appendLine()
        sql.append("ORDER BY ")
        if (trimmed.isNotBlank() && sort == LibraryTrackSortMode.Title && useFts) {
            sql.append(
                """
                CASE
                    WHEN library_tracks.normalizedTitle LIKE ? THEN 0
                    WHEN library_tracks.normalizedArtist LIKE ? THEN 1
                    WHEN library_tracks.normalizedAlbum LIKE ? THEN 2
                    ELSE 3
                END,
                """.trimIndent(),
            )
            sql.appendLine()
        }
        sql.append(trackSortOrder(sort))
        return sql.toString()
    }

    fun buildTrackQueueSql(
        query: String,
        useFts: Boolean,
        localSources: Boolean,
        limit: Int,
    ): String {
        val sql = StringBuilder()
        sql.append("SELECT library_tracks.* FROM library_tracks")
        val trimmed = query.trim()
        val sourceSql = if (localSources) {
            LibraryScanPolicy.LocalSourceSql
        } else {
            LibraryScanPolicy.RemoteSourceSql
        }
        if (trimmed.isNotBlank() && useFts) {
            sql.appendLine()
            sql.append("JOIN library_tracks_fts ON library_tracks.id = library_tracks_fts.trackId")
            sql.appendLine()
            sql.append("WHERE library_tracks_fts MATCH ?")
            sql.append(" AND ")
            sql.append(sourceSql)
            sql.appendLine()
            sql.append(
                """
                ORDER BY
                    CASE
                        WHEN library_tracks.normalizedTitle LIKE ? THEN 0
                        WHEN library_tracks.normalizedArtist LIKE ? THEN 1
                        WHEN library_tracks.normalizedAlbum LIKE ? THEN 2
                        ELSE 3
                    END,
                    library_tracks.title COLLATE NOCASE ASC
                """.trimIndent(),
            )
        } else if (trimmed.isNotBlank()) {
            sql.appendLine()
            sql.append(
                """
                WHERE (library_tracks.normalizedTitle LIKE ?
                   OR library_tracks.normalizedArtist LIKE ?
                   OR library_tracks.normalizedAlbum LIKE ?
                   OR library_tracks.pinyinTitle LIKE ?
                   OR library_tracks.pinyinArtist LIKE ?
                   OR library_tracks.pinyinAlbum LIKE ?)
                """.trimIndent(),
            )
            sql.append(" AND ")
            sql.append(sourceSql)
            sql.appendLine()
            sql.append(
                """
                ORDER BY
                    CASE
                        WHEN library_tracks.normalizedTitle LIKE ? THEN 0
                        WHEN library_tracks.normalizedArtist LIKE ? THEN 1
                        WHEN library_tracks.normalizedAlbum LIKE ? THEN 2
                        ELSE 3
                    END,
                    library_tracks.title COLLATE NOCASE ASC
                """.trimIndent(),
            )
        } else {
            sql.appendLine()
            sql.append("WHERE ")
            sql.append(sourceSql)
            sql.appendLine()
            sql.append("ORDER BY library_tracks.title COLLATE NOCASE ASC")
        }
        sql.appendLine()
        sql.append("LIMIT ")
        sql.append(limit.coerceAtLeast(1))
        return sql.toString()
    }

    fun usesFtsMatchWithoutLeadingWildcardOr(sql: String): Boolean {
        val compact = sql.replace(Regex("\\s+"), " ")
        if (!compact.contains("MATCH ?", ignoreCase = true)) return false
        if (Regex("OR\\s+[^\\s]+\\s+LIKE\\s+'%", RegexOption.IGNORE_CASE).containsMatchIn(compact)) return false
        if (Regex("OR\\s+library_tracks\\.[A-Za-z]+\\s+LIKE\\s+\\?", RegexOption.IGNORE_CASE).containsMatchIn(compact)) {
            return false
        }
        return true
    }

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
