package app.echo.android.data

import app.echo.android.model.library.LibrarySource

data class LibraryScanCompleteness(
    val querySucceeded: Boolean,
    val scannedCount: Int,
    val existingCount: Int,
    val hitVisitCap: Boolean = false,
)

object LibraryScanPolicy {
    const val MediaStoreNativeIdPrefix = "mediastore:"
    const val SafTrackIdPrefix = "saf:"
    const val LocalSourceSql = "(source = 'mediastore' OR source = 'saf')"
    const val RemoteSourceSql = "(source != 'mediastore' AND source != 'saf')"

    fun shouldDeleteMissingLibraryRows(completeness: LibraryScanCompleteness): Boolean {
        if (!completeness.querySucceeded) return false
        if (completeness.hitVisitCap) return false
        if (completeness.scannedCount <= 0 && completeness.existingCount > 0) return false
        return true
    }

    fun isLocalLibrarySource(source: String): Boolean =
        source == LibrarySource.MediaStore.id || source == SafSourceId

    fun isRemoteLibrarySource(source: String): Boolean = !isLocalLibrarySource(source)

    fun isMediaStoreNativeId(trackId: String): Boolean = trackId.startsWith(MediaStoreNativeIdPrefix)

    fun isSafTrackId(trackId: String): Boolean = trackId.startsWith(SafTrackIdPrefix)

    fun shouldDeleteOnFullMediaStoreCleanup(trackId: String): Boolean = isMediaStoreNativeId(trackId)

    fun shouldDeleteOnDocumentTreeCleanup(trackId: String): Boolean = isSafTrackId(trackId)

    fun shouldPreserveUserMetadata(
        incomingFingerprint: String?,
        existingFingerprint: String?,
        metadataEditedAtEpochMs: Long?,
    ): Boolean = metadataEditedAtEpochMs != null && incomingFingerprint != existingFingerprint

    fun scanRowAction(existingFingerprint: String?, incomingFingerprint: String?): LibraryScanRowAction =
        when {
            existingFingerprint == null -> LibraryScanRowAction.Insert
            existingFingerprint != incomingFingerprint -> LibraryScanRowAction.Update
            else -> LibraryScanRowAction.RememberSeen
        }

    fun shouldStampLastSeenOnUnchangedRow(): Boolean = false

    fun unseenIds(existingIds: Collection<String>, seenIds: Set<String>): List<String> =
        existingIds.distinct().filterNot(seenIds::contains)

    fun usesDocumentTreeScan(volume: String): Boolean =
        !volume.equals("primary", ignoreCase = true)

    fun shouldReuseUnchangedDocumentFingerprint(
        existingContentUri: String,
        incomingContentUri: String,
        existingSizeBytes: Long,
        incomingSizeBytes: Long,
        existingDateModifiedSeconds: Long,
        incomingDateModifiedSeconds: Long,
    ): Boolean =
        existingContentUri.isNotBlank() &&
            existingContentUri == incomingContentUri &&
            existingSizeBytes == incomingSizeBytes &&
            existingDateModifiedSeconds == incomingDateModifiedSeconds

    const val SafSourceId = "saf"
}

enum class LibraryScanRowAction {
    Insert,
    Update,
    RememberSeen,
}

data class MediaStoreScanOutcome(
    val scannedCount: Int,
    val querySucceeded: Boolean,
)

data class RemoteSyncVisit(
    val visitedCount: Int,
    val hitVisitCap: Boolean,
)
