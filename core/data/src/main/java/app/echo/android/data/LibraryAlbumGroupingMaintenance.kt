package app.echo.android.data

import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.coroutineContext

/** Scan and tag edits load the same complete folder/disc siblings before regrouping. */
internal class LibraryAlbumGroupingMaintenance(
    private val dao: LibraryTrackDao,
    private val batchSize: Int,
    private val dispatcher: CoroutineDispatcher = LibraryScanDispatchers.Limited,
    private val rebuildSummaries: suspend (LibrarySummaryKeySet) -> Unit,
) {
    suspend fun reconcileAlbums(albums: Set<String>, folders: Set<String>): List<LibraryTrackEntity> =
        withContext(dispatcher) {
            val tracks = LinkedHashMap<String, LibraryTrackEntity>()
            for (album in albums.filter { it.isNotBlank() && !LibraryMetadataSentinels.isUnknown(it) }) {
                coroutineContext.ensureActive()
                // All supported disc suffixes begin with a separator below U+FFFF.
                // The indexed prefix range is only a candidate set; the policy is authoritative.
                for (track in dao.getLocalAlbumGroupingCandidates(album, "$album\uFFFF")) {
                    val identity = LibraryAlbumGrouping.groupingIdentity(track.normalizedAlbum, track.album, track.relativePath)
                    if (identity.album == album && identity.folder in folders) tracks[track.id] = track
                }
                yield()
            }
            reconcile(tracks.values.toList())
        }

    suspend fun reconcileFolders(folders: Set<String>): List<LibraryTrackEntity> = withContext(dispatcher) {
        if (folders.isEmpty()) return@withContext emptyList()
        if (LibraryScanPolicy.shouldRebuildLibrarySummariesIncrementally(
                changedKeyCount = folders.size,
                existingAlbumSummaryCount = dao.countAlbumSummaries(),
            )) {
            val ids = LibraryAlbumGrouping.trackIdsInGroupingFolders(dao.getLocalIdPaths(), folders)
            val loaded = ArrayList<LibraryTrackEntity>(ids.size)
            ids.chunked(batchSize).forEach { chunk ->
                coroutineContext.ensureActive()
                loaded += dao.getTracksByIds(chunk)
                yield()
            }
            reconcile(loaded)
        } else {
            // Full rebuilds keep only the grouping projection in memory; full rows are
            // fetched per chunk once regrouping has decided which tracks actually changed.
            reconcileRows(dao.getLocalLibraryRegroupRows())
        }
    }

    private suspend fun reconcile(tracks: List<LibraryTrackEntity>): List<LibraryTrackEntity> {
        val changed = LibraryAlbumGrouping.reconcile(tracks)
        if (changed.isEmpty()) return emptyList()
        val before = tracks.associateBy { it.id }
        val summaryKeys = LibrarySummaryKeyAccumulator()
        changed.forEach { next ->
            before[next.id]?.let { summaryKeys.add(it.toSummaryKeySet()) }
            summaryKeys.add(next.toSummaryKeySet())
        }
        return persistRegrouped(changed, summaryKeys)
    }

    private suspend fun reconcileRows(rows: List<LibraryAlbumRegroupRow>): List<LibraryTrackEntity> {
        val changed = LibraryAlbumGrouping.reconcileRows(rows)
        if (changed.isEmpty()) return emptyList()
        val before = rows.associateBy { it.id }
        val summaryKeys = LibrarySummaryKeyAccumulator()
        changed.forEach { row ->
            before[row.id]?.let { summaryKeys.add(it.toSummaryKeyRow().toSummaryKeySet()) }
            summaryKeys.add(row.toSummaryKeyRow().toSummaryKeySet())
        }
        val newKeys = changed.associate { it.id to it.albumKey }
        val regrouped = ArrayList<LibraryTrackEntity>(changed.size)
        changed.map { it.id }.chunked(batchSize).forEach { chunk ->
            coroutineContext.ensureActive()
            dao.getTracksByIds(chunk).forEach { entity ->
                newKeys[entity.id]?.let { regrouped += entity.copy(albumKey = it) }
            }
            yield()
        }
        return persistRegrouped(regrouped, summaryKeys)
    }

    private suspend fun persistRegrouped(
        regrouped: List<LibraryTrackEntity>,
        summaryKeys: LibrarySummaryKeyAccumulator,
    ): List<LibraryTrackEntity> {
        regrouped.chunked(batchSize).forEach { chunk ->
            coroutineContext.ensureActive()
            dao.upsertBatchWithFts(chunk)
            yield()
        }
        rebuildSummaries(summaryKeys.build())
        return regrouped
    }

    private fun LibraryAlbumRegroupRow.toSummaryKeyRow() = TrackSummaryKeyRow(
        albumKey = albumKey,
        artistKey = artistKey,
        relativePath = relativePath,
        source = source,
        genreKey = genreKey,
        artist = artist,
    )
}
