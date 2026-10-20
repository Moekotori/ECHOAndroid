package app.echo.android.data

import java.io.File
import androidx.room.withTransaction
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

/** One-time, paged repair. Normal formats and manually edited/CUE rows are excluded by the query. */
internal suspend fun backfillExtendedAudioMetadata(
    database: EchoLibraryDatabase,
    directory: File,
    read: (LibraryTrackEntity) -> LocalAudioContainerInfo?,
    persistArtwork: (ByteArray?) -> String?,
) {
    val marker = File(directory, "local-extended-audio-metadata-v2")
    if (marker.exists()) return
    val dao = database.trackDao()
    var after = ""
    var inspected = false
    while (true) {
        coroutineContext.ensureActive()
        val page = dao.getExtendedAudioBackfillPage(after, 16)
        if (page.isEmpty()) break
        inspected = true
        val facts = page.mapNotNull { track ->
            coroutineContext.ensureActive()
            read(track)?.let { info ->
                val artwork = persistArtwork(info.tags?.artworkBytes)
                // Do not retain a page of cover images or lyrics while reading the next files.
                track.id to (info.copy(tags = info.tags?.copy(artworkBytes = null, lyrics = null)) to artwork)
            }
        }.toMap()
        // Refresh after I/O so an edit or file replacement made meanwhile is preserved.
        database.withTransaction {
            val current = dao.getTracksByIds(page.map { it.id }).associateBy { it.id }
            val updates = page.mapNotNull { original ->
                val track = current[original.id] ?: return@mapNotNull null
                if (track.metadataEditedAtEpochMs != null || track.contentUri != original.contentUri ||
                    track.sizeBytes != original.sizeBytes || track.dateModifiedSeconds != original.dateModifiedSeconds) return@mapNotNull null
                val (info, artwork) = facts[original.id] ?: return@mapNotNull null
                val next = track.withExtendedAudioMetadata(info) { artwork }
                next.takeUnless { it == track }
            }
            if (updates.isNotEmpty()) {
                dao.upsertBatchWithFts(updates)
            }
        }
        after = page.last().id
        yield()
    }
    // Also repairs summaries after an interrupted prior pass whose rows were already saved.
    if (inspected) dao.rebuildLibrarySummaries()
    runCatching { marker.writeText("1") }
}

internal fun LibraryTrackEntity.withExtendedAudioMetadata(
    info: LocalAudioContainerInfo,
    persistArtwork: (ByteArray?) -> String?,
): LibraryTrackEntity {
    if (metadataEditedAtEpochMs != null || clipStartMs != 0L || clipEndMs != 0L ||
        app.echo.android.model.library.CueSheetPolicy.isCueTrackId(id)) return this
    return withAudioTags(info.tags).copy(
        durationMs = info.durationMs.takeIf { it > 0 } ?: durationMs,
        sampleRateHz = info.sampleRateHz ?: sampleRateHz,
        artworkUri = persistArtwork(info.tags?.artworkBytes) ?: artworkUri,
    ).withScanMetadata(lastSeenScanRunId)
}
