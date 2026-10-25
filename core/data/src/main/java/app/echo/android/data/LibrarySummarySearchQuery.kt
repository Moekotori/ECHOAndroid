package app.echo.android.data

import androidx.sqlite.db.SimpleSQLiteQuery

/** Preserve category matching/ranking while indexing the song-to-category lookup. */
internal object LibrarySummarySearchQuery {
    fun searchAlbums(query: String, limit: Int? = null): SimpleSQLiteQuery = bind(AlbumSql, query, limit)
    fun searchAllAlbums(query: String, limit: Int? = null): SimpleSQLiteQuery =
        bind(AlbumSql.replace("WHERE isRemote = 0\n          AND (", "WHERE ("), query, limit)
    fun searchArtists(query: String, limit: Int? = null): SimpleSQLiteQuery = bind(ArtistSql, query, limit)

    private val Parameters = Regex(":(query|limit|candidate)\\b")

    private fun bind(template: String, query: String, limit: Int?): SimpleSQLiteQuery {
        val candidate = substringFtsQuery(query)
        val bounded = if (limit == null) template.replace("LIMIT :limit", "") else template
        val indexed = if (candidate == null) bounded else bounded.replace(
            "FROM visible_library_tracks AS matched_track",
            "FROM library_tracks_fts CROSS JOIN visible_library_tracks AS matched_track " +
                "ON matched_track.id = library_tracks_fts.trackId AND library_tracks_fts MATCH :candidate",
        )
        val args = mutableListOf<Any>()
        val sql = Parameters.replace(indexed) { match ->
            args += when (match.groupValues[1]) {
                "candidate" -> requireNotNull(candidate)
                "limit" -> requireNotNull(limit)
                else -> query
            }
            "?"
        }
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    private const val AlbumSql = """
SELECT albumKey, title, albumArtist, artist, artworkUri, trackCount, durationMs, year, addedAtSeconds
        FROM library_album_summaries
        WHERE isRemote = 0
          AND (
            title LIKE '%' || :query || '%'
            OR albumArtist LIKE '%' || :query || '%'
            OR artist LIKE '%' || :query || '%'
            OR pinyinTitle LIKE '%' || lower(:query) || '%'
            OR pinyinArtist LIKE '%' || lower(:query) || '%'
            OR albumKey IN (
                SELECT matched_track.albumKey
                FROM visible_library_tracks AS matched_track
                WHERE (matched_track.source = 'mediastore' OR matched_track.source = 'saf')
                  AND (
                    matched_track.normalizedTitle LIKE '%' || lower(:query) || '%'
                    OR matched_track.normalizedArtist LIKE '%' || lower(:query) || '%'
                    OR matched_track.normalizedAlbum LIKE '%' || lower(:query) || '%'
                    OR matched_track.normalizedAlbumArtist LIKE '%' || lower(:query) || '%'
                    OR matched_track.pinyinTitle LIKE '%' || lower(:query) || '%'
                    OR matched_track.pinyinArtist LIKE '%' || lower(:query) || '%'
                    OR matched_track.pinyinAlbum LIKE '%' || lower(:query) || '%'
                  )
            )
          )
        ORDER BY
            CASE
                WHEN title LIKE :query || '%' THEN 0
                WHEN pinyinTitle LIKE lower(:query) || '%' THEN 1
                WHEN albumArtist LIKE :query || '%' THEN 2
                WHEN pinyinArtist LIKE lower(:query) || '%' THEN 3
                ELSE 4
            END,
            title COLLATE NOCASE ASC, albumKey ASC
        LIMIT :limit
    """
    private const val ArtistSql = """
SELECT artistKey, name, artworkUri, albumCount, trackCount, durationMs
        FROM library_artist_summaries
        WHERE name LIKE '%' || :query || '%'
           OR pinyinName LIKE '%' || lower(:query) || '%'
           OR artistKey IN (
                SELECT credits.artistKey
                FROM visible_library_tracks AS matched_track
                JOIN library_track_artists AS credits ON credits.trackId = matched_track.id
                WHERE (matched_track.source = 'mediastore' OR matched_track.source = 'saf')
                  AND (
                    matched_track.normalizedTitle LIKE '%' || lower(:query) || '%'
                    OR matched_track.normalizedArtist LIKE '%' || lower(:query) || '%'
                    OR matched_track.normalizedAlbum LIKE '%' || lower(:query) || '%'
                    OR matched_track.normalizedAlbumArtist LIKE '%' || lower(:query) || '%'
                    OR matched_track.pinyinTitle LIKE '%' || lower(:query) || '%'
                    OR matched_track.pinyinArtist LIKE '%' || lower(:query) || '%'
                    OR matched_track.pinyinAlbum LIKE '%' || lower(:query) || '%'
                  )
           )
        ORDER BY
            CASE
                WHEN name LIKE :query || '%' THEN 0
                WHEN pinyinName LIKE lower(:query) || '%' THEN 1
                ELSE 2
            END,
            name COLLATE NOCASE ASC, artistKey ASC
        LIMIT :limit
    """
}
