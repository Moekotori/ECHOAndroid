package app.echo.android.data

import androidx.room.withTransaction
import app.echo.android.model.library.*
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

suspend fun EchoLibraryRepository.batchFavorites(ids: List<String>, favorite: Boolean) = withContext(Dispatchers.IO) {
    require(ids.size <= 200)
    database.withTransaction {
        val dao = database.playlistDao()
        if (!favorite) database.trackDao().deleteFavoritesByTrackIds(ids)
        else ids.distinct().forEach { if (!dao.isFavorite(it)) dao.upsertFavorite(LibraryFavoriteEntity(it, System.currentTimeMillis())) }
    }
}

suspend fun EchoLibraryRepository.batchAddToPlaylist(id: String, tracks: List<String>) = withContext(Dispatchers.IO) {
    require(tracks.size <= 200)
    database.withTransaction {
        val dao = database.playlistDao()
        val playlist = dao.getPlaylist(id) ?: error("Playlist no longer exists")
        require(playlist.source == "mediastore" && !id.startsWith(EchoSmartPlaylistRule.IdPrefix))
        val merged = (dao.getPlaylistTrackIds(id) + tracks).distinct()
        dao.replacePlaylist(playlist.copy(trackCount = merged.size, updatedAtEpochMs = System.currentTimeMillis()),
            merged.mapIndexed { position, trackId -> LibraryPlaylistTrackEntity(id, trackId, position) })
    }
}

suspend fun EchoLibraryRepository.batchTags(ids: List<String>, patch: EchoBatchTagPatch,
    onProgress: (Int) -> Unit, writer: (suspend (EchoTrackMetadataUpdate) -> TrackMetadataUpdateResult)? = null): EchoBatchResult = withContext(Dispatchers.IO) {
    require(ids.size <= 200)
    require(listOf(patch.artist, patch.album, patch.albumArtist, patch.composer, patch.genre).all { it == null || it.length <= 300 })
    require(patch.year == null || patch.year in 0..9999)
    var completed = 0; var failed = 0; var fileFailed = 0
    val currentRows = database.trackDao().getTracksByIds(ids).associateBy(LibraryTrackEntity::id)
    for (id in ids.distinct()) {
        coroutineContext.ensureActive()
        val row = currentRows[id]
        if (row == null) { failed++ } else try {
            val update = EchoTrackMetadataUpdate(id, row.title, patch.artist ?: row.artist,
                patch.album ?: row.album, patch.albumArtist ?: row.albumArtist, row.trackNumber, row.discNumber,
                if (patch.year == null) row.year else patch.year?.takeIf { it > 0 }, patch.composer ?: row.composer, genre = patch.genre)
            val result = writer?.invoke(update) ?: updateTrackMetadata(update)
            if (result.indexUpdated) completed++ else failed++
            if (result.fileWrite !is EmbeddedTagWriteResult.Written) fileFailed++
        } catch (e: CancellationException) { throw e } catch (_: Exception) { failed++ }
        onProgress(completed + failed)
        yield()
    }
    EchoBatchResult(completed, failed, fileFailed)
}
