package app.echo.android.data

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import app.echo.android.model.library.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

/** Lists at most 500 unavailable entries. Opening the tool again continues after repaired rows. */
suspend fun EchoLibraryRepository.inspectLibraryFiles(): List<EchoLibraryRepairItem> = withContext(Dispatchers.IO) {
    val result = ArrayList<EchoLibraryRepairItem>()
    var after = ""
    while (result.size < 500) {
        val rows = database.trackDao().queryTracks(SimpleSQLiteQuery(
            "SELECT * FROM library_tracks WHERE source IN ('mediastore','saf') AND id > ? ORDER BY id LIMIT 200", arrayOf(after)))
        if (rows.isEmpty()) break
        for (row in rows) {
            coroutineContext.ensureActive()
            if (!localUriExists(CueSheetPolicy.playbackUri(row.contentUri))) {
                result += EchoLibraryRepairItem(row.toEchoTrack(), row.relativePath ?: row.contentUri)
                if (result.size == 500) break
            }
        }
        after = rows.last().id
        yield()
    }
    val present = result.map { it.track.id }.toHashSet()
    database.experienceDao().archives(500 - result.size).forEach { archive ->
        if (archive.id !in present) {
            val track = decodeRepairTrack(archive)
            result += EchoLibraryRepairItem(track.toEchoTrack(), track.relativePath ?: track.contentUri)
        }
    }
    result
}

suspend fun EchoLibraryRepository.repairCandidates(track: EchoTrack): List<EchoTrack> = withContext(Dispatchers.IO) {
    val original = if (localUriExists(CueSheetPolicy.playbackUri(track.uri))) listOf(track) else emptyList()
    original + database.trackDao().queryTracks(SimpleSQLiteQuery(
        "SELECT * FROM library_tracks WHERE source IN ('mediastore','saf') AND id != ? " +
            "AND normalizedTitle = ? ORDER BY CASE WHEN lower(trim(artist)) = lower(trim(?)) THEN 0 ELSE 1 END, " +
            "ABS(durationMs - ?), id LIMIT 20", arrayOf<Any>(track.id, track.title.normalizedForSearch(), track.artist, track.durationMs)))
        .filter { localUriExists(CueSheetPolicy.playbackUri(it.contentUri)) }.map { it.toEchoTrack() }
}

suspend fun EchoLibraryRepository.relinkLibraryTrack(oldId: String, candidate: EchoTrack) = withContext(Dispatchers.IO) {
    require(localUriExists(CueSheetPolicy.playbackUri(candidate.uri)))
    database.withTransaction {
        val dao = database.trackDao()
        val archive = database.experienceDao().archived(oldId)
        val old = dao.getTrackById(oldId) ?: archive?.let { decodeRepairTrack(it).also { row -> dao.upsertBatchWithFts(listOf(row)) } }
            ?: error("Track no longer exists")
        if (archive != null) restoreRepairReferences(archive)
        val originalFragment = android.net.Uri.parse(old.contentUri).fragment
        val replacementUri = if (old.clipEndMs > old.clipStartMs && !originalFragment.isNullOrBlank())
            android.net.Uri.parse(CueSheetPolicy.playbackUri(candidate.uri)).buildUpon().fragment(originalFragment).build().toString()
            else candidate.uri
        val existing = dao.getTrackByContentUri(replacementUri)
        if (existing?.id == oldId && replacementUri == old.contentUri) {
            dao.refreshMergedPlaylistCounts(oldId)
            database.experienceDao().deleteArchive(oldId)
            return@withTransaction
        }
        if (existing != null && existing.id != oldId) {
            dao.mergeScanDuplicate(oldId, existing.id)
        } else {
            // SAF identities agree with the folder scanner, so future scans retain the repaired track.
            val uri = android.net.Uri.parse(CueSheetPolicy.playbackUri(candidate.uri))
            val documentId = runCatching { android.provider.DocumentsContract.getDocumentId(uri) }.getOrNull()
            val baseId = documentId?.let { "saf:${android.net.Uri.encode(it)}" }
                ?: "saf:relinked:" + java.security.MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray()).joinToString("") { "%02x".format(it) }
            val newId = if (old.clipEndMs > old.clipStartMs) "$baseId#cue:${oldId.substringAfterLast("#cue:", old.clipStartMs.toString())}" else baseId
            val updated = old.copy(id = newId, contentUri = replacementUri, source = LibrarySource.Saf.id,
                sizeBytes = candidate.sizeBytes, dateModifiedSeconds = candidate.dateModifiedSeconds,
                sampleRateHz = candidate.sampleRateHz ?: old.sampleRateHz,
                durationMs = if (old.clipEndMs > old.clipStartMs) old.durationMs else candidate.durationMs,
                relativePath = null, fileName = null).withFingerprint()
            dao.upsertBatchWithFts(listOf(updated))
            dao.mergeScanDuplicate(oldId, newId)
        }
        database.experienceDao().deleteArchive(oldId)
    }
}
