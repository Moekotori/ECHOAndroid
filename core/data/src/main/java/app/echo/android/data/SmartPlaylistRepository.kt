package app.echo.android.data

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import app.echo.android.model.library.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

fun smartPlaylistQuery(id: String, limit: Int? = null, anchorId: String? = null, offset: Int = 0): SimpleSQLiteQuery {
    val order = "CASE r.sort WHEN 'RecentlyAdded' THEN -t.dateModifiedSeconds WHEN 'LeastPlayed' THEN COALESCE(s.playCount,0) WHEN 'MostPlayed' THEN -COALESCE(s.playCount,0) ELSE 0 END, t.title COLLATE NOCASE, t.id"
    val from = "library_tracks t JOIN library_smart_rules r LEFT JOIN library_playback_stats s ON s.trackId = t.id"
    val args = mutableListOf<Any>(id)
    val anchor = if (anchorId == null) "" else {
        args.add(id); args.add(anchorId)
        " AND ($order) >= (SELECT $order FROM $from WHERE r.playlistId = ? AND t.id = ?)"
    }
    return SimpleSQLiteQuery("SELECT t.* FROM $from WHERE r.playlistId = ? AND ($SmartRuleMatchSql)$anchor ORDER BY $order" +
        if (limit != null) " LIMIT ${limit.coerceIn(1, 10000)} OFFSET ${offset.coerceAtLeast(0)}" else "", args.toTypedArray())
}

suspend fun EchoLibraryRepository.saveSmartPlaylist(id: String?, name: String, rule: EchoSmartPlaylistRule): EchoPlaylist =
    withContext(Dispatchers.IO) {
        require(name.trim().length in 1..100 && rule.isValid)
        val key = id?.takeIf { it.startsWith(EchoSmartPlaylistRule.IdPrefix) }
            ?: EchoSmartPlaylistRule.IdPrefix + UUID.randomUUID()
        val now = System.currentTimeMillis()
        database.withTransaction {
            val pinned = database.experienceDao().rule(key)?.pinned ?: false
            database.playlistDao().upsertPlaylist(LibraryPlaylistEntity(key, name.trim(), "mediastore", null, 0, now))
            database.experienceDao().saveRule(LibrarySmartRuleEntity(key, rule.artist.trim(), rule.genre.trim(),
                rule.favoriteOnly, rule.notPlayedDays, rule.minimumYear, rule.maximumYear, rule.sort.name, pinned,
                rule.matchAny, rule.album.trim(), rule.folder.trim(), rule.format.trim(),
                rule.minimumDurationSeconds, rule.maximumDurationSeconds, rule.excludeText.trim()))
        }
        EchoPlaylist(key, name.trim(), updatedAtEpochMs = now)
    }

suspend fun EchoLibraryRepository.smartPlaylistRule(id: String): EchoSmartPlaylistRule? =
    withContext(Dispatchers.IO) { database.experienceDao().rule(id)?.toRule() }

suspend fun EchoLibraryRepository.pinSmartPlaylistToHome(id: String, pinned: Boolean): Boolean = withContext(Dispatchers.IO) {
    database.withTransaction {
        val rule = database.experienceDao().rule(id) ?: return@withTransaction false
        if (pinned && !rule.pinned && database.experienceDao().homePinCount() >= 8) return@withTransaction false
        database.experienceDao().setHomePin(id, pinned)
        true
    }
}

suspend fun EchoLibraryRepository.previewSmartPlaylist(rule: EchoSmartPlaylistRule): EchoSmartPlaylistPreview =
    withContext(Dispatchers.IO) {
        require(rule.isValid)
        // A bound one-row relation previews an unsaved rule without writing to the library.
        val relation = "(SELECT ? AS artist, ? AS genre, ? AS favoriteOnly, ? AS notPlayedDays, ? AS minimumYear, ? AS maximumYear, ? AS sort, ? AS matchAny, ? AS album, ? AS folder, ? AS format, ? AS minimumDurationSeconds, ? AS maximumDurationSeconds, ? AS excludeText) r"
        val args = arrayOf<Any>(rule.artist.trim(), rule.genre.trim(), if (rule.favoriteOnly) 1 else 0,
            rule.notPlayedDays, rule.minimumYear, rule.maximumYear, rule.sort.name, if (rule.matchAny) 1 else 0,
            rule.album.trim(), rule.folder.trim(), rule.format.trim(), rule.minimumDurationSeconds, rule.maximumDurationSeconds, rule.excludeText.trim())
        val from = "FROM library_tracks t CROSS JOIN $relation LEFT JOIN library_playback_stats s ON s.trackId = t.id WHERE $SmartRuleMatchSql"
        val count = database.openHelper.readableDatabase.query(SimpleSQLiteQuery("SELECT COUNT(*) $from", args)).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        val order = "CASE r.sort WHEN 'RecentlyAdded' THEN -t.dateModifiedSeconds WHEN 'LeastPlayed' THEN COALESCE(s.playCount,0) WHEN 'MostPlayed' THEN -COALESCE(s.playCount,0) ELSE 0 END, t.title COLLATE NOCASE, t.id"
        val tracks = database.trackDao().queryTracks(SimpleSQLiteQuery("SELECT t.* $from ORDER BY $order LIMIT 5", args))
        EchoSmartPlaylistPreview(count, tracks.map { it.toEchoTrack() })
    }

fun EchoLibraryRepository.observeBookmarks(trackId: String) =
    database.experienceDao().bookmarks(trackId).map { rows -> rows.map { it.toBookmark() } }

suspend fun EchoLibraryRepository.saveBookmark(trackId: String, positionMs: Long, label: String) = withContext(Dispatchers.IO) {
    val track = database.trackDao().getTrackById(trackId)
    require(positionMs >= 0 && label.trim().length in 1..100)
    val position = if (track != null && track.durationMs > 0) positionMs.coerceAtMost(track.durationMs - 1) else positionMs
    // A repeated save at the same point edits the existing bookmark rather than duplicating it.
    database.experienceDao().saveBookmark(LibraryBookmarkEntity("$trackId:$position", trackId, position, label.trim()))
}

suspend fun EchoLibraryRepository.saveBookmark(track: EchoTrack, positionMs: Long, label: String) = withContext(Dispatchers.IO) {
    require(positionMs >= 0 && label.trim().length in 1..100)
    val position = if (track.durationMs > 0) positionMs.coerceAtMost(track.durationMs - 1) else positionMs
    database.experienceDao().saveBookmark(LibraryBookmarkEntity("${track.id}:$position", track.id, position, label.trim(),
        track.title, track.artist, track.uri, track.durationMs))
}

suspend fun EchoLibraryRepository.deleteBookmark(id: String) = withContext(Dispatchers.IO) {
    database.experienceDao().deleteBookmark(id)
}
