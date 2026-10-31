package app.echo.android.data

/** Read-only SQL plans; every external identifier/value is bound, never interpolated. */
internal data class LibraryDocumentsSql(val sql: String, val args: List<String>, val order: String)

internal object LibraryDocumentsQueries {
    const val Root = "library"
    const val Albums = "albums"
    const val Playlists = "playlists"
    const val All = "all"
    private const val CanonicalId = "CASE WHEN instr(t.id, '#cue:') > 0 THEN substr(t.id, 1, instr(t.id, '#cue:') - 1) ELSE t.id END"
    private const val Local = "t.source IN ('mediastore','saf') AND (t.contentUri LIKE 'content:%' OR t.contentUri LIKE 'file:%')"
    private const val OrdinaryPlaylist = "p.source IN ('mediastore','saf','local') AND NOT EXISTS (SELECT 1 FROM library_smart_rules r WHERE r.playlistId=p.id)"
    val Columns = arrayOf("document_id", "_display_name", "mime_type", "flags", "_size", "last_modified", "summary")
    private val formats = linkedMapOf("mp3" to "audio/mpeg", "flac" to "audio/flac", "m4a" to "audio/mp4",
        "aac" to "audio/aac", "ogg" to "audio/ogg", "opus" to "audio/ogg", "wav" to "audio/wav",
        "wave" to "audio/wav", "aif" to "audio/aiff", "aiff" to "audio/aiff", "ape" to "audio/x-ape",
        "wv" to "audio/x-wavpack", "dsf" to "audio/dsf", "dff" to "audio/dff", "wma" to "audio/x-ms-wma",
        "tta" to "audio/x-tta", "tak" to "audio/x-tak", "mpc" to "audio/x-musepack", "w64" to "audio/x-w64")
    private val mime = "CASE WHEN t.mimeType LIKE 'audio/%' AND t.mimeType NOT LIKE '%*%' THEN t.mimeType " +
        "WHEN lower(t.mimeType) IN ('application/flac','application/x-flac') THEN 'audio/flac' " +
        "WHEN lower(t.mimeType) IN ('application/ogg','application/x-ogg') THEN 'audio/ogg' " +
        formats.entries.joinToString(" ") { (extension, type) -> "WHEN lower(t.fileName) LIKE '%.$extension' THEN '$type'" } +
        " ELSE 'audio/x-unknown' END"

    fun payload(id: String, prefix: String): String? = id.takeIf { it.startsWith(prefix) && it.length <= 2048 }
        ?.removePrefix(prefix)?.takeIf { it.isNotEmpty() }

    private fun albums(unknown: String, flags: Int, key: String? = null): LibraryDocumentsSql = LibraryDocumentsSql("""
        SELECT 'album:' || t.albumKey AS document_id, COALESCE(NULLIF(MAX(t.album),''),?) AS _display_name,
               'vnd.android.document/directory' AS mime_type, $flags AS flags, NULL AS _size,
               MAX(t.dateModifiedSeconds)*1000 AS last_modified, MAX(COALESCE(t.albumArtist,t.artist)) AS summary
        FROM visible_library_tracks t WHERE $Local AND t.albumKey<>'' ${if (key != null) "AND t.albumKey=?" else ""}
        GROUP BY t.albumKey
    """.trimIndent(), listOf(unknown) + listOfNotNull(key), "_display_name COLLATE NOCASE, document_id")

    private fun playlists(flags: Int, key: String? = null): LibraryDocumentsSql = LibraryDocumentsSql("""
        SELECT 'playlist:' || p.id AS document_id, p.name AS _display_name,
               'vnd.android.document/directory' AS mime_type, $flags AS flags, NULL AS _size,
               p.updatedAtEpochMs AS last_modified, NULL AS summary
        FROM library_playlists p WHERE $OrdinaryPlaylist ${if (key != null) "AND p.id=?" else ""}
    """.trimIndent(), listOfNotNull(key), "_display_name COLLATE NOCASE, document_id")

    private fun tracks(whole: String, where: String = "", args: List<String> = emptyList(), playlist: Boolean = false): LibraryDocumentsSql {
        val joins = if (playlist) "JOIN library_playlist_tracks m ON m.trackId=t.id JOIN library_playlists p ON p.id=m.playlistId" else ""
        return LibraryDocumentsSql("""
            SELECT 'track:' || $CanonicalId AS document_id,
                   COALESCE(NULLIF(t.fileName,''), NULLIF(t.title,''), 'audio') AS _display_name,
                   $mime AS mime_type, 0 AS flags, CASE WHEN t.sizeBytes>0 THEN t.sizeBytes ELSE NULL END AS _size,
                   MAX(t.dateModifiedSeconds)*1000 AS last_modified,
                   CASE WHEN instr(t.id,'#cue:')>0 OR t.clipStartMs>0 OR t.clipEndMs>0 THEN ? ELSE t.artist END AS summary,
                   ${if (playlist) "MIN(m.position)" else "0"} AS _position
            FROM visible_library_tracks t $joins WHERE $Local $where
            GROUP BY $CanonicalId
        """.trimIndent(), listOf(whole) + args, if (playlist) "_position, document_id" else "_display_name COLLATE NOCASE, document_id")
    }

    fun children(parent: String, unknown: String, whole: String, flags: Int): LibraryDocumentsSql? = when {
        parent == Albums -> albums(unknown, flags)
        parent == Playlists -> playlists(flags)
        parent == All -> tracks(whole)
        payload(parent, "album:") != null -> tracks(whole, "AND t.albumKey=?", listOf(payload(parent, "album:")!!))
        payload(parent, "playlist:") != null -> tracks(whole, "AND p.id=? AND $OrdinaryPlaylist", listOf(payload(parent, "playlist:")!!), true)
        else -> null
    }

    fun document(id: String, unknown: String, whole: String, flags: Int): LibraryDocumentsSql? = when {
        payload(id, "album:") != null -> albums(unknown, flags, payload(id, "album:"))
        payload(id, "playlist:") != null -> playlists(flags, payload(id, "playlist:"))
        payload(id, "track:") != null -> trackRange(id)?.let { (where, args) -> tracks(whole, where, args) }
        else -> null
    }

    private fun trackRange(id: String): Pair<String, List<String>>? {
        val base = payload(id, "track:")?.takeUnless { "#cue:" in it } ?: return null
        return "AND (t.id=? OR (t.id>=? AND t.id<?))" to listOf(base, "$base#cue:", "$base#cue;")
    }

    fun source(id: String): LibraryDocumentsSql? = trackRange(id)?.let { (where, args) ->
        LibraryDocumentsSql("SELECT t.contentUri FROM visible_library_tracks t WHERE $Local $where ORDER BY t.id LIMIT 1", args, "")
    }

    fun search(query: String, whole: String): LibraryDocumentsSql {
        val pattern = "%" + query.take(256).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        return tracks(whole, "AND (t.title LIKE ? ESCAPE '\\' OR t.artist LIKE ? ESCAPE '\\' OR t.album LIKE ? ESCAPE '\\' OR t.fileName LIKE ? ESCAPE '\\')",
            List(4) { pattern })
    }

    fun project(plan: LibraryDocumentsSql, projection: Array<out String>?, order: String = plan.order, limit: Int? = null, offset: Int = 0): LibraryDocumentsSql {
        val columns = projection?.takeIf { it.isNotEmpty() } ?: Columns
        require(columns.size <= 64 && columns.all { it.length <= 128 })
        val select = columns.joinToString(",") { column ->
            val quoted = "\"${column.replace("\"", "\"\"")}\""
            if (column in Columns) quoted else "NULL AS $quoted"
        }
        val pagination = if (limit != null || offset > 0) " LIMIT ${limit?.coerceAtLeast(1) ?: -1} OFFSET ${offset.coerceAtLeast(0)}" else ""
        return plan.copy(sql = "SELECT $select FROM (${plan.sql}) documents" + (if (order.isEmpty()) "" else " ORDER BY $order") + pagination)
    }

    fun sort(order: String?): String? {
        val parts = order?.trim()?.split(Regex("\\s+")) ?: return null
        val column = parts.firstOrNull()?.takeIf { it in setOf("_display_name", "last_modified", "_size") } ?: return null
        val direction = parts.getOrNull(1)?.uppercase()?.takeIf { it in setOf("ASC", "DESC") } ?: if (parts.size == 1) "ASC" else return null
        if (parts.size > 2) return null
        return "$column ${if (column == "_display_name") "COLLATE NOCASE " else ""}$direction, document_id"
    }
}
