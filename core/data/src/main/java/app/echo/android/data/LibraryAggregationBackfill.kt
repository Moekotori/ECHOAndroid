package app.echo.android.data

import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

/** Only lightweight identities span the library; complete entities live for one album group. */
data class LibraryAlbumGroupingRow(
    val id: String,
    val normalizedAlbum: String?,
    val album: String?,
    val relativePath: String?,
)

internal suspend fun backfillLibraryAggregationKeys(
    dao: LibraryTrackDao,
    batchSize: Int,
    prepareTrack: (LibraryTrackEntity) -> LibraryTrackEntity,
): Boolean {
    var changed = false
    val preservedIds = HashSet<String>()
    var afterId: String? = null
    while (true) {
        coroutineContext.ensureActive()
        val rows = if (afterId == null) dao.getAggregationBackfillFirstTracks(batchSize)
            else dao.getAggregationBackfillTracksAfter(afterId, batchSize)
        if (rows.isEmpty()) break
        val updates = ArrayList<LibraryTrackEntity>(rows.size)
        for (track in rows) {
            coroutineContext.ensureActive()
            val prepared = prepareTrack(track)
            val cleaned = prepared.withPreservedAlbumGrouping(track)
            if (cleaned.albumKey != prepared.albumKey) preservedIds += track.id
            if (!track.hasSameAggregationMetadata(cleaned)) updates += cleaned
        }
        if (updates.isNotEmpty()) {
            dao.upsertBatchWithFts(updates)
            changed = true
        }
        afterId = rows.last().id
        yield()
    }

    val groups = LinkedHashMap<LibraryAlbumGrouping.GroupIdentity, MutableList<String>>()
    afterId = null
    while (true) {
        coroutineContext.ensureActive()
        val rows = if (afterId == null) dao.getLocalAlbumGroupingFirstRows(batchSize)
            else dao.getLocalAlbumGroupingRowsAfter(afterId, batchSize)
        if (rows.isEmpty()) break
        for (row in rows) {
            val identity = LibraryAlbumGrouping.groupingIdentity(row.normalizedAlbum, row.album, row.relativePath)
            // Non-reconcilable groups already received their canonical key above.
            if (identity.canReconcile) groups.getOrPut(identity, ::ArrayList).add(row.id)
        }
        afterId = rows.last().id
        yield()
    }
    val iterator = groups.entries.iterator()
    val singletonIds = ArrayList<String>()
    while (iterator.hasNext()) {
        coroutineContext.ensureActive()
        val ids = iterator.next().value
        iterator.remove()
        if (ids.size < 2) {
            ids.singleOrNull()?.takeIf { it in preservedIds }?.let(singletonIds::add)
            continue
        }
        val tracks = ArrayList<LibraryTrackEntity>(ids.size)
        ids.chunked(batchSize).forEach { chunk ->
            coroutineContext.ensureActive()
            tracks.addAll(dao.getTracksByIds(chunk))
        }
        val updates = LibraryAlbumGrouping.reconcile(tracks)
        updates.chunked(batchSize).forEach { chunk ->
            coroutineContext.ensureActive()
            dao.upsertBatchWithFts(chunk)
            changed = true
            yield()
        }
        yield()
    }
    // Reset old inferred keys for surviving singletons without one DAO query per album.
    singletonIds.chunked(batchSize).forEach { ids ->
        coroutineContext.ensureActive()
        val updates = LibraryAlbumGrouping.reconcile(dao.getTracksByIds(ids))
        if (updates.isNotEmpty()) {
            dao.upsertBatchWithFts(updates)
            changed = true
        }
        yield()
    }
    return changed
}

private fun LibraryTrackEntity.hasSameAggregationMetadata(other: LibraryTrackEntity): Boolean =
    title == other.title && artist == other.artist && album == other.album && albumArtist == other.albumArtist &&
        albumKey == other.albumKey && artistKey == other.artistKey && normalizedTitle == other.normalizedTitle &&
        normalizedArtist == other.normalizedArtist && normalizedAlbum == other.normalizedAlbum &&
        normalizedAlbumArtist == other.normalizedAlbumArtist
