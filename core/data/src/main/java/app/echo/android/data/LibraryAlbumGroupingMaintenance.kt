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
        val tracks = if (LibraryScanPolicy.shouldRebuildLibrarySummariesIncrementally(
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
            loaded
        } else dao.getLocalLibraryTracks()
        reconcile(tracks)
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
        changed.chunked(batchSize).forEach { chunk ->
            coroutineContext.ensureActive()
            dao.upsertBatchWithFts(chunk)
            yield()
        }
        rebuildSummaries(summaryKeys.build())
        return changed
    }
}
