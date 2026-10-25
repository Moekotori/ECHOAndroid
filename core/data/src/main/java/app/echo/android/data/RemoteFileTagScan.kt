package app.echo.android.data

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** Migration marker plus file facts avoid downloading tags again for unchanged remote files. */
internal class RemoteFileTagScan(
    directory: File,
    sourceId: String,
    private val reader: (LibraryTrackEntity) -> AudioTagFields?,
    private val persistArtwork: (ByteArray?) -> String?,
    private val persistLyrics: (LibraryTrackEntity, String?) -> Unit = { _, _ -> },
) {
    private val marker = File(directory, "remote-file-tags-v1-${sourceId.hashCode().toUInt().toString(16)}")
    private val backfill = !marker.exists()

    fun canReuse(incoming: LibraryTrackEntity, existing: TrackFingerprint?): Boolean =
        !backfill && existing != null && !existing.fingerprint.isNullOrBlank() &&
            existing.fingerprint != LibraryScanPolicy.PendingDocumentMetadataFingerprint &&
            !CueScanState.isWholeFileCue(existing.fingerprint) &&
            !app.echo.android.model.library.CueSheetPolicy.isCueTrackId(existing.id) &&
            incoming.contentUri == existing.contentUri && incoming.sizeBytes > 0 &&
            incoming.sizeBytes == existing.sizeBytes && incoming.dateModifiedSeconds > 0 &&
            incoming.dateModifiedSeconds == existing.dateModifiedSeconds &&
            incoming.relativePath == existing.relativePath && !existing.fileName.isNullOrBlank() &&
            incoming.fileName == existing.fileName

    fun enrich(incoming: LibraryTrackEntity, existing: LibraryTrackEntity?): LibraryTrackEntity {
        if (existing?.metadataEditedAtEpochMs != null) return incoming
        val unchanged = sameRemoteFile(incoming, existing)
        val reusable = unchanged && !CueScanState.isWholeFileCue(existing?.fingerprint) &&
            existing?.fingerprint != LibraryScanPolicy.PendingDocumentMetadataFingerprint
        if (!backfill && reusable && existing != null && existing.fingerprint != LibraryScanPolicy.PendingDocumentMetadataFingerprint) {
            return existing.copy(lastSeenScanRunId = incoming.lastSeenScanRunId)
        }
        val tags = try { reader(incoming) } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            return (if (unchanged && existing != null) existing.copy(lastSeenScanRunId = incoming.lastSeenScanRunId) else incoming)
                .copy(fingerprint = LibraryScanPolicy.PendingDocumentMetadataFingerprint)
        } catch (_: Exception) {
            return (if (unchanged && existing != null) existing.copy(lastSeenScanRunId = incoming.lastSeenScanRunId) else incoming)
                .copy(fingerprint = LibraryScanPolicy.PendingDocumentMetadataFingerprint)
        }
        val fallback = if (reusable && existing != null) existing.copy(lastSeenScanRunId = incoming.lastSeenScanRunId, fingerprint = null) else incoming
        persistLyrics(incoming, tags?.lyrics)
        return fallback.withAudioTags(tags).copy(
            artworkUri = persistArtwork(tags?.artworkBytes) ?: fallback.artworkUri,
        ).withScanMetadata(incoming.lastSeenScanRunId)
    }

    fun complete() {
        // Failed rows carry their own retry fingerprint; do not repeat every successful file.
        runCatching { marker.writeText("1") }
    }
}

internal fun sameRemoteFile(incoming: LibraryTrackEntity, existing: LibraryTrackEntity?): Boolean =
    existing != null && existing.contentUri == incoming.contentUri &&
        incoming.dateModifiedSeconds > 0 && incoming.dateModifiedSeconds == existing.dateModifiedSeconds &&
        incoming.sizeBytes > 0 && incoming.sizeBytes == existing.sizeBytes &&
        incoming.relativePath == existing.relativePath
