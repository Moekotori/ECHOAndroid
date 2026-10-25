package app.echo.android.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.map
import androidx.room.withTransaction
import app.echo.android.model.library.EchoHiddenTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

fun EchoLibraryRepository.observeHiddenTrackCount() = database.hiddenTrackDao().count()
fun EchoLibraryRepository.pagedHiddenTracks() = Pager(PagingConfig(pageSize = 40, maxSize = 200,
    enablePlaceholders = false)) { database.hiddenTrackDao().page() }.flow.map { page ->
    page.map { EchoHiddenTrack(it.trackId, it.title, it.artist, it.album, it.hiddenAtEpochMs) }
}

suspend fun EchoLibraryRepository.hideTracks(ids: List<String>): Set<String> = withContext(Dispatchers.IO) {
    val changed = linkedSetOf<String>()
    ids.distinct().chunked(256).forEach { batch -> database.withTransaction {
        val tracks = database.trackDao().getTracksByIds(batch)
        database.hiddenTrackDao().hide(tracks.map { LibraryHiddenTrackEntity(it.id, it.title, it.artist, it.album, System.currentTimeMillis()) })
        refreshHiddenSummaryKeys(tracks.map { it.id })
        changed.addAll(tracks.map { it.id })
    } }
    changed
}

suspend fun EchoLibraryRepository.restoreHiddenTracks(ids: List<String>) = withContext(Dispatchers.IO) {
    var restored = 0
    ids.distinct().chunked(256).forEach { batch -> database.withTransaction {
        val hidden = database.hiddenTrackDao().hiddenIds(batch)
        restored += database.hiddenTrackDao().restore(hidden)
        refreshHiddenSummaryKeys(hidden)
    } }
    restored
}

suspend fun EchoLibraryRepository.restoreAllHiddenTracks() = withContext(Dispatchers.IO) {
    var restored = 0
    while (true) {
        val batch = database.hiddenTrackDao().firstBatch()
        if (batch.isEmpty()) break
        restored += restoreHiddenTracks(batch)
        yield()
    }
    restored
}

suspend fun EchoLibraryRepository.hiddenTrackIds(ids: List<String>): Set<String> = withContext(Dispatchers.IO) {
    ids.distinct().chunked(256).flatMap { database.hiddenTrackDao().hiddenIds(it) }.toSet()
}

private suspend fun EchoLibraryRepository.refreshHiddenSummaryKeys(ids: List<String>) {
    val keys = LibrarySummaryKeyAccumulator()
    ids.chunked(256).forEach { batch -> database.trackDao().getSummaryKeyRows(batch).forEach { keys.add(it.toSummaryKeySet()) } }
    val affected = keys.build()
    database.trackDao().rebuildLibrarySummariesForKeys(affected.albumKeys, affected.artistKeys, affected.folderKeys, affected.genreKeys)
}
