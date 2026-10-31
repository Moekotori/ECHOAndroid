package app.echo.android.data

import androidx.sqlite.db.SimpleSQLiteQuery

/** Separate keys keep an album artist's details and queue independent of the display preference. */
internal object LibraryAlbumArtistQuery {
    private const val Prefix = "album-artist:"
    fun owns(key: String): Boolean = key.startsWith(Prefix)

    // BLOB slicing also preserves text after NUL separators (SQLite text length stops at NUL).
    private const val NameSeparator = "(CASE WHEN instr(rest, x'00') BETWEEN 1 AND instr(rest, x'3b') THEN instr(rest, x'00') ELSE instr(rest, x'3b') END)"
    private const val KeySeparator = "(CASE WHEN instr(keyRest, x'00') BETWEEN 1 AND instr(keyRest, x'3b') THEN instr(keyRest, x'00') ELSE instr(keyRest, x'3b') END)"

    // Split only explicit tag separators, just like LibraryArtistPolicy. The normalized tag
    // supplies Unicode-aware keys; fallback memberships reuse the existing song artist index.
    val memberships = """
        WITH RECURSIVE album_artist_tags AS (
            SELECT id, albumArtist AS name,
                   COALESCE(NULLIF(normalizedAlbumArtist, ''), lower(trim(albumArtist))) AS nameKey
            FROM visible_library_tracks
            WHERE source IN ('mediastore', 'saf') AND trim(COALESCE(albumArtist, '')) != ''
              AND lower(trim(albumArtist)) NOT IN ('<unknown>', 'unknown artist', '未知艺术家', '不明なアーティスト')
        ), album_artist_parts(trackId, rest, keyRest, name, nameKey) AS (
            SELECT id, CAST(replace(name, '；', ';') || ';' AS BLOB),
                   CAST(replace(nameKey, '；', ';') || ';' AS BLOB), '', ''
            FROM album_artist_tags
            UNION ALL
            SELECT trackId, substr(rest, $NameSeparator + 1), substr(keyRest, $KeySeparator + 1),
                   trim(CAST(substr(rest, 1, $NameSeparator - 1) AS TEXT)),
                   trim(CAST(substr(keyRest, 1, $KeySeparator - 1) AS TEXT))
            FROM album_artist_parts WHERE length(rest) > 0
        ), album_artist_memberships AS (
            SELECT DISTINCT trackId, '$Prefix' || nameKey AS artistKey, name
            FROM album_artist_parts WHERE name != '' AND nameKey != ''
            UNION
            SELECT a.trackId, '$Prefix' || a.artistKey, a.name
            FROM library_track_artists a JOIN visible_library_tracks t ON t.id = a.trackId
            WHERE t.source IN ('mediastore', 'saf')
              AND t.id NOT IN (SELECT trackId FROM album_artist_parts WHERE name != '' AND nameKey != '')
        )
    """.trimIndent()

    private val summaries = """
        SELECT a.artistKey, MIN(a.name) AS name, MAX(t.artworkUri) AS artworkUri,
               COUNT(DISTINCT t.albumKey) AS albumCount, COUNT(DISTINCT t.id) AS trackCount,
               COALESCE(SUM(t.durationMs), 0) AS durationMs
        FROM (SELECT trackId, artistKey, MIN(name) AS name FROM album_artist_memberships GROUP BY trackId, artistKey) a
        JOIN visible_library_tracks t ON t.id = a.trackId
    """.trimIndent()

    fun artists(query: String?, sort: String): SimpleSQLiteQuery {
        val order = when (sort) {
            "AlbumCount" -> "albumCount DESC, "
            "TrackCount" -> "trackCount DESC, "
            "Duration" -> "durationMs DESC, "
            else -> ""
        } + "name COLLATE NOCASE, artistKey"
        val search = if (query == null) "" else " WHERE name LIKE '%' || ? || '%' OR substr(artistKey, ${Prefix.length + 1}) LIKE '%' || ? || '%'"
        return SimpleSQLiteQuery("$memberships SELECT * FROM ($summaries GROUP BY a.artistKey)$search ORDER BY $order",
            if (query == null) emptyArray() else arrayOf(query, query.normalizedForSearch().ifBlank { query.trim() }))
    }

    fun summary(key: String) = SimpleSQLiteQuery(
        "$memberships $summaries WHERE a.artistKey = ? GROUP BY a.artistKey", arrayOf(key))

    fun albums(key: String) = SimpleSQLiteQuery("""
        $memberships
        SELECT albumKey, title, albumArtist, artist, artworkUri, trackCount, durationMs, year, addedAtSeconds
        FROM library_album_summaries WHERE isRemote = 0 AND albumKey IN (
            SELECT t.albumKey FROM visible_library_tracks t
            JOIN album_artist_memberships a ON a.trackId = t.id WHERE a.artistKey = ?
        ) ORDER BY year DESC, title COLLATE NOCASE, albumKey
    """.trimIndent(), arrayOf(key))

    fun tracks(key: String, query: String? = null, sort: String = "Album", limit: Int? = null): SimpleSQLiteQuery {
        val order = when (sort) {
            "Title" -> "title COLLATE NOCASE, "
            "Duration" -> "durationMs DESC, "
            else -> ""
        } + "album COLLATE NOCASE, CASE WHEN discNumber > 0 THEN discNumber ELSE 1 END, COALESCE(trackNumber, 0), title COLLATE NOCASE, id"
        val args = mutableListOf<Any>(key)
        val search = if (query == null) "" else {
            args += query; args += query
            " AND (instr(lower(title), lower(?)) > 0 OR instr(lower(COALESCE(album, '')), lower(?)) > 0)"
        }
        val window = if (limit == null) "" else " LIMIT ?".also { args += limit.coerceAtLeast(1) }
        return SimpleSQLiteQuery("$memberships SELECT * FROM visible_library_tracks WHERE id IN " +
            "(SELECT trackId FROM album_artist_memberships WHERE artistKey = ?)$search ORDER BY $order$window", args.toTypedArray())
    }
}
