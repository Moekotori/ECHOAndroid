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
    private var failed = false

    fun enrich(incoming: LibraryTrackEntity, existing: LibraryTrackEntity?): LibraryTrackEntity {
        if (existing?.metadataEditedAtEpochMs != null) return incoming
        val unchanged = sameRemoteFile(incoming, existing)
        if (!backfill && unchanged && existing != null) {
            return existing.copy(lastSeenScanRunId = incoming.lastSeenScanRunId)
        }
        val tags = try { reader(incoming) } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            failed = true
            return if (unchanged && existing != null) existing.copy(lastSeenScanRunId = incoming.lastSeenScanRunId) else incoming
        } catch (_: Exception) {
            failed = true
            return if (unchanged && existing != null) existing.copy(lastSeenScanRunId = incoming.lastSeenScanRunId) else incoming
        }
        val fallback = if (unchanged && existing != null) existing.copy(lastSeenScanRunId = incoming.lastSeenScanRunId) else incoming
        persistLyrics(incoming, tags?.lyrics)
        return fallback.withAudioTags(tags).copy(
            artworkUri = persistArtwork(tags?.artworkBytes) ?: fallback.artworkUri,
        ).withScanMetadata(incoming.lastSeenScanRunId)
    }

    fun complete() {
        if (!failed) runCatching { marker.writeText("1") }
    }
}

internal fun sameRemoteFile(incoming: LibraryTrackEntity, existing: LibraryTrackEntity?): Boolean =
    existing != null && existing.contentUri == incoming.contentUri &&
        incoming.dateModifiedSeconds > 0 && incoming.dateModifiedSeconds == existing.dateModifiedSeconds &&
        incoming.sizeBytes > 0 && incoming.sizeBytes == existing.sizeBytes &&
        incoming.relativePath == existing.relativePath
