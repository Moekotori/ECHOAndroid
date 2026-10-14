package app.echo.android.data

import android.database.Cursor
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import app.echo.android.model.connect.EchoPhoneCatalogEntry
import app.echo.android.model.connect.EchoPhoneCatalogPage
import app.echo.android.model.connect.EchoPhoneCatalogQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** A session-owned, read-only projection of the phone's actual local categories. */
class EchoSharedLocalCatalog(private val database: EchoLibraryDatabase) {
    private val folders = linkedMapOf<String, String>()
    private val local = """WITH local AS (SELECT * FROM library_tracks
        WHERE source IN ('mediastore','saf') AND (contentUri LIKE 'content:%' OR contentUri LIKE 'file:%'))"""
    private val cover = "MAX(CASE WHEN t.artworkUri LIKE 'content:%' OR t.artworkUri LIKE 'file:%' THEN t.id END) AS coverId"

    suspend fun page(query: EchoPhoneCatalogQuery): EchoPhoneCatalogPage = withContext(Dispatchers.IO) {
        database.withTransaction {
            val args = mutableListOf<Any>()
            val body = if (query.kind == "songs" || query.kind == "liked") tracks(query, args) else groups(query, args)
            val pattern = "%" + query.search.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            val filtered = "$local SELECT * FROM ($body) WHERE (? = '' OR title LIKE ? ESCAPE '\\' OR subtitle LIKE ? ESCAPE '\\')"
            args.addAll(listOf(query.search.trim(), pattern, pattern))
            val total = database.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM ($filtered)", args.toTypedArray())).use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
            val order = if (query.parentKind == "playlist" || query.kind == "liked") "sortOrder, title COLLATE NOCASE, id"
                else if (query.parentKind == "album") "discNumber, trackNumber, title COLLATE NOCASE, id"
                else "title COLLATE NOCASE, id"
            val rows = database.query(SimpleSQLiteQuery("$filtered ORDER BY $order LIMIT 50 OFFSET ?",
                (args + (query.page - 1) * 50).toTypedArray())).use { cursor ->
                buildList { while (cursor.moveToNext()) add(entry(cursor)) }
            }
            EchoPhoneCatalogPage(rows, total, query.page,
                parentKind = query.parentKind.takeIf { query.kind == "albums" && it == "artist" },
                parentId = query.parentId.takeIf { query.kind == "albums" && query.parentKind == "artist" })
        }
    }

    private fun tracks(query: EchoPhoneCatalogQuery, args: MutableList<Any>): String {
        var from = "local t"
        var where = "1 = 1"
        var order = "0"
        when {
            query.kind == "liked" -> { from += " JOIN library_favorites f ON f.trackId = t.id"; order = "-f.favoritedAtEpochMs" }
            query.parentKind == "album" -> { where = "t.albumKey = ?"; args.add(query.parentId!!) }
            query.parentKind == "artist" -> {
                where = "EXISTS(SELECT 1 FROM library_track_artists a WHERE a.trackId = t.id AND a.artistKey = ?)"
                args.add(query.parentId!!)
            }
            query.parentKind == "genre" -> { where = "t.genreKey = ?"; args.add(query.parentId!!) }
            query.parentKind == "folder" -> {
                val path = synchronized(folders) { folders[query.parentId] } ?: error("folder_expired")
                where = "COALESCE(t.relativePath,'') = ?"; args.add(path)
            }
            query.parentKind == "playlist" -> {
                val id = query.parentId!!
                val smart = database.query(SimpleSQLiteQuery("SELECT 1 FROM library_smart_rules WHERE playlistId = ?", arrayOf(id))).use { it.moveToFirst() }
                if (smart) {
                    from += " JOIN library_smart_rules r LEFT JOIN library_playback_stats s ON s.trackId = t.id"
                    where = "r.playlistId = ? AND ($SmartRuleMatchSql)"
                    order = "CASE r.sort WHEN 'RecentlyAdded' THEN -t.dateModifiedSeconds WHEN 'LeastPlayed' THEN COALESCE(s.playCount,0) WHEN 'MostPlayed' THEN -COALESCE(s.playCount,0) ELSE 0 END"
                } else {
                    from += " JOIN library_playlist_tracks p ON p.trackId = t.id"
                    where = "p.playlistId = ?"; order = "p.position"
                }
                args.add(id)
            }
        }
        return """SELECT 'track' AS kind, t.id, t.title, COALESCE(t.artist,'') || ' · ' || COALESCE(t.album,'') AS subtitle,
            0 AS count, CASE WHEN t.artworkUri LIKE 'content:%' OR t.artworkUri LIKE 'file:%' THEN t.id END AS coverId,
            t.artist, t.album, t.durationMs, t.sampleRateHz AS sampleRate, t.discNumber, t.trackNumber,
            CASE WHEN t.clipStartMs = 0 AND t.clipEndMs = 0 AND t.id NOT LIKE '%#cue:%'
                AND COALESCE(t.mimeType,'') NOT LIKE '%dsd%' AND COALESCE(t.mimeType,'') NOT LIKE '%dsf%'
                AND COALESCE(t.mimeType,'') NOT LIKE '%dff%' THEN 1 ELSE 0 END AS canPlay,
            $order AS sortOrder FROM $from WHERE $where"""
    }

    private fun groups(query: EchoPhoneCatalogQuery, args: MutableList<Any>): String = when (query.kind) {
        "albums" -> {
            val scope = if (query.parentKind == "artist") {
                args.add(query.parentId!!)
                "WHERE t.albumKey IN (SELECT a.albumKey FROM local a JOIN library_track_artists credit ON credit.trackId=a.id WHERE credit.artistKey=?)"
            } else ""
            "SELECT 'album' AS kind,t.albumKey AS id,COALESCE(t.album,'') AS title,COALESCE(t.albumArtist,t.artist,'') AS subtitle,COUNT(*) AS count,$cover FROM local t $scope GROUP BY t.albumKey"
        }
        "artists" -> "SELECT 'artist' AS kind,a.artistKey AS id,s.name AS title,'' AS subtitle,COUNT(DISTINCT t.id) AS count,$cover FROM local t JOIN library_track_artists a ON a.trackId=t.id JOIN library_artist_summaries s ON s.artistKey=a.artistKey GROUP BY a.artistKey"
        "genres" -> "SELECT 'genre' AS kind,t.genreKey AS id,COALESCE(t.genre,'') AS title,'' AS subtitle,COUNT(*) AS count,$cover FROM local t GROUP BY t.genreKey"
        "folders" -> "SELECT 'folder' AS kind,COALESCE(t.relativePath,'') AS id,COALESCE(t.relativePath,'') AS title,'' AS subtitle,COUNT(*) AS count,$cover FROM local t GROUP BY COALESCE(t.relativePath,'')"
        "playlists" -> """SELECT 'playlist' AS kind,p.id,p.name AS title,'' AS subtitle,
            CASE WHEN r.playlistId IS NULL THEN (SELECT COUNT(*) FROM library_playlist_tracks pt JOIN local t ON t.id=pt.trackId WHERE pt.playlistId=p.id)
            ELSE (SELECT COUNT(*) FROM local t LEFT JOIN library_playback_stats s ON s.trackId=t.id WHERE $SmartRuleMatchSql) END AS count,
            (SELECT $cover FROM local t JOIN library_playlist_tracks pt ON pt.trackId=t.id WHERE pt.playlistId=p.id) AS coverId
            FROM library_playlists p LEFT JOIN library_smart_rules r ON r.playlistId=p.id WHERE p.source IN ('mediastore','saf')"""
        else -> error("unsupported_category")
    }

    private fun entry(cursor: Cursor): EchoPhoneCatalogEntry {
        fun value(name: String): String? = cursor.getColumnIndex(name).takeIf { it >= 0 }?.let { if (cursor.isNull(it)) null else cursor.getString(it) }
        val kind = value("kind")!!
        val rawId = value("id")!!
        val id = if (kind == "folder") rememberFolder(rawId) else rawId
        val title = if (kind == "folder") rawId.trimEnd('/').substringAfterLast('/').ifBlank { "本地音乐" } else value("title").orEmpty()
        return EchoPhoneCatalogEntry(kind, id, title, value("subtitle").orEmpty(), value("count")?.toInt() ?: 0,
            value("coverId"), value("artist"), value("album"), value("durationMs")?.toLong(),
            value("canPlay")?.let { it == "1" }, value("sampleRate")?.toInt())
    }

    private fun rememberFolder(path: String): String = synchronized(folders) {
        folders.entries.firstOrNull { it.value == path }?.key ?: UUID.randomUUID().toString().also {
            if (folders.size >= 512) folders.remove(folders.keys.first())
            folders[it] = path
        }
    }
}
