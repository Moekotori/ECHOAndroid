package app.echo.android.data

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import app.echo.android.model.connect.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

private suspend fun EchoLibraryRepository.syncStateRows(key: String): Pair<LibraryPlaylistEntity?,List<LibraryTrackEntity>> {
    if (key == "favorites") return null to database.trackDao().queryTracks(SimpleSQLiteQuery(
        "SELECT t.* FROM library_tracks t JOIN library_favorites f ON f.trackId=t.id WHERE t.source IN ('mediastore','saf') ORDER BY f.favoritedAtEpochMs DESC,t.id LIMIT 10001"))
    val id = syncLocalId(key)
    val row = database.playlistDao().getPlaylist(id)?.takeIf { it.source == "mediastore" && !id.startsWith(app.echo.android.model.library.EchoSmartPlaylistRule.IdPrefix) }
    return row to if (row == null) emptyList() else database.playlistDao().listPlaylistTracksForBrowse(id,10001,0)
}
private fun revision(state: EchoSyncState): String {
    val text = JSONObject().put("key",state.key).put("name",state.name).put("exists",state.exists).put("tracks",JSONArray().apply {
        state.tracks.forEach { put(JSONArray(listOf(it.title,it.artist,it.album,it.durationMs))) }
    }).toString()
    return java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
suspend fun EchoLibraryRepository.syncSnapshot(key: String): EchoSyncState = withContext(Dispatchers.IO) {
    require(key == "favorites" || key.startsWith("pc:") || key.startsWith("android:local:"))
    val (row,tracks) = syncStateRows(key)
    require(tracks.size <= 10000)
    val state = EchoSyncState(key,row?.name.orEmpty(),key == "favorites" || row != null,
        tracks.map { EchoSyncTrackRef(it.title,it.artist,it.album,it.durationMs) })
    state.copy(revision = revision(state))
}
suspend fun EchoLibraryRepository.replaceSyncState(expected: String,desired: EchoSyncState): EchoSyncApplyResult = withContext(Dispatchers.IO) {
    require(desired.tracks.size <= 10000 && desired.name.length <= 100)
    database.withTransaction {
        val current = syncSnapshot(desired.key)
        check(current.revision == expected) { "Collection changed after preview" }
        val dao = database.playlistDao()
        if (!desired.exists) {
            require(desired.key != "favorites")
            dao.deletePlaylist(syncLocalId(desired.key))
            return@withTransaction EchoSyncApplyResult(0,0,false)
        }
        val (row,currentRows) = syncStateRows(desired.key)
        val own = currentRows.groupBy { EchoSyncReconcile.trackKey(EchoSyncTrackRef(it.title,it.artist,it.album,it.durationMs)) }
        val wanted = mutableListOf<String>(); var missing = 0
        desired.tracks.forEach { ref ->
            currentCoroutineContext().ensureActive()
            val known = own[EchoSyncReconcile.trackKey(ref)]
            if (known != null) wanted += known.map(LibraryTrackEntity::id)
            else uniqueSyncMatch(ref)?.let { wanted += it.id } ?: run { missing++ }
        }
        val ids = if (missing > 0) (currentRows.map(LibraryTrackEntity::id) + wanted).distinct() else wanted.distinct()
        if (desired.key == "favorites") {
            val existing = currentRows.map(LibraryTrackEntity::id).toSet()
            if (missing == 0) (existing - ids.toSet()).toList().chunked(500).forEach { database.trackDao().deleteFavoritesByTrackIds(it) }
            ids.filterNot(existing::contains).forEach { dao.upsertFavorite(LibraryFavoriteEntity(it,System.currentTimeMillis())) }
        } else {
            val id = syncLocalId(desired.key)
            require(!id.startsWith(app.echo.android.model.library.EchoSmartPlaylistRule.IdPrefix) && id != app.echo.android.model.library.EchoPlaylist.LikedSongsId)
            dao.replacePlaylist(LibraryPlaylistEntity(id,desired.name.ifBlank { row?.name ?: "Playlist" },"mediastore",row?.artworkUri,ids.size,System.currentTimeMillis()),
                ids.mapIndexed { index,trackId -> LibraryPlaylistTrackEntity(id,trackId,index) })
        }
        EchoSyncApplyResult(wanted.size,missing,missing > 0)
    }
}
