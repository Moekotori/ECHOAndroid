package app.echo.android.data

import android.util.Base64
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import app.echo.android.model.connect.*
import app.echo.android.model.library.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

private const val SyncPlaylistPrefix = "local:sync:"
private fun canonicalSyncKey(id: String): String = if (id.startsWith(SyncPlaylistPrefix))
    String(Base64.decode(id.removePrefix(SyncPlaylistPrefix), Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8) else "android:$id"
internal fun syncLocalId(key: String): String = if (key.startsWith("android:local:")) key.removePrefix("android:")
    else SyncPlaylistPrefix + Base64.encodeToString(key.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

suspend fun EchoLibraryRepository.syncCollections(): List<EchoSyncCollection> = withContext(Dispatchers.IO) {
    val rows = database.playlistDao().getPlaylistsBySource(LibrarySource.MediaStore.id)
        .filterNot { it.id.startsWith(EchoSmartPlaylistRule.IdPrefix) || LibrarySmartPlaylistKind.fromId(it.id) != null }
    require(rows.size <= 500)
    val count = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM library_favorites f JOIN library_tracks t ON t.id = f.trackId WHERE t.source IN ('mediastore','saf')").use {
        if (it.moveToFirst()) it.getInt(0) else 0
    }
    listOf(EchoSyncCollection("favorites", "", count, true)) + rows.map {
        EchoSyncCollection(canonicalSyncKey(it.id), it.name, it.trackCount)
    }
}

suspend fun EchoLibraryRepository.syncExportBatch(collection: EchoSyncCollection, offset: Int): EchoSyncBatch = withContext(Dispatchers.IO) {
    val rows = if (collection.favorites) database.trackDao().queryTracks(SimpleSQLiteQuery(
        "SELECT t.* FROM library_tracks t JOIN library_favorites f ON f.trackId = t.id WHERE t.source IN ('mediastore','saf') " +
            "ORDER BY f.favoritedAtEpochMs DESC, t.id LIMIT 200 OFFSET ?", arrayOf(offset)))
        else database.playlistDao().listPlaylistTracksForBrowse(syncLocalId(collection.key), 200, offset)
    EchoSyncBatch(collection, rows.filter { it.source in listOf("mediastore", "saf") }.map {
        EchoSyncTrackRef(it.title, it.artist, it.album, it.durationMs)
    })
}

suspend fun EchoLibraryRepository.syncImportBatch(batch: EchoSyncBatch, preview: Boolean): EchoSyncBatchResult = withContext(Dispatchers.IO) {
    require(batch.tracks.size <= 200 && batch.collection.key.length in 1..512 && batch.collection.name.length <= 100)
    val dao = database.trackDao()
    val ids = ArrayList<String>()
    var skipped = 0
    for (ref in batch.tracks) {
        coroutineContext.ensureActive()
        val match = uniqueSyncMatch(ref)
        if (match != null) ids += match.id else skipped++
    }
    if (!preview) database.withTransaction {
        val playlists = database.playlistDao()
        if (batch.collection.favorites) ids.distinct().forEach {
            if (!playlists.isFavorite(it)) playlists.upsertFavorite(LibraryFavoriteEntity(it, System.currentTimeMillis()))
        } else {
            val id = syncLocalId(batch.collection.key)
            require(!id.startsWith(EchoSmartPlaylistRule.IdPrefix) && LibrarySmartPlaylistKind.fromId(id) == null && id != EchoPlaylist.LikedSongsId)
            val previous = playlists.getPlaylist(id)
            val currentIds = playlists.getPlaylistTrackIds(id)
            val merged = (currentIds + ids).distinct()
            require(merged.size <= 10000)
            val first = merged.firstOrNull()?.let { dao.getTrackById(it) }
            playlists.replacePlaylist(LibraryPlaylistEntity(id, previous?.name ?: batch.collection.name,
                "mediastore", previous?.artworkUri ?: first?.artworkUri, merged.size, System.currentTimeMillis()),
                merged.mapIndexed { index, trackId -> LibraryPlaylistTrackEntity(id, trackId, index) })
        }
    }
    EchoSyncBatchResult(ids.size, skipped)
}

/** Indexed candidate lookup; incomplete or ambiguous results never pick an arbitrary edition. */
internal suspend fun EchoLibraryRepository.uniqueSyncMatch(ref: EchoSyncTrackRef): LibraryTrackEntity? {
    val candidates = database.trackDao().queryTracks(SimpleSQLiteQuery(
        "SELECT * FROM library_tracks WHERE source IN ('mediastore','saf') AND " +
            "(normalizedTitle = ? OR (normalizedTitle IS NULL AND lower(trim(title)) = ?)) LIMIT 101",
        arrayOf(ref.title.normalizedForSearch(), ref.title.trim().lowercase(java.util.Locale.ROOT))))
    if (candidates.size > 100) return null
    fun equal(left: String, right: String) = left.trim().equals(right.trim(), ignoreCase = true)
    return candidates.filter { equal(it.title, ref.title) && equal(it.artist, ref.artist) &&
        (ref.album.isNullOrBlank() || equal(it.album.orEmpty(), ref.album.orEmpty())) &&
        (ref.durationMs == 0L || kotlin.math.abs(it.durationMs - ref.durationMs) <= 2000) }.singleOrNull()
}
