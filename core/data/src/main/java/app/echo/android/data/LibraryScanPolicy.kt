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

    fun shouldRefreshLocalLibraryAfterPermissionGrant(localMediaStoreCount: Int): Boolean =
        localMediaStoreCount <= 0

    fun usesDocumentTreeScan(volume: String): Boolean =
        !volume.equals("primary", ignoreCase = true)

    fun splitDocumentTreeId(documentId: String): Pair<String, String>? {
        val trimmed = documentId.trim()
        if (trimmed.isBlank()) return null
        val parts = trimmed.split(":", limit = 2)
        val volume = parts.first().trim()
        if (volume.isBlank()) return null
        val path = parts.getOrNull(1)
            ?.replace('\\', '/')
            ?.trim('/')
            .orEmpty()
        return volume to path
    }

    fun documentTreeRelativePath(volume: String, path: String): String? =
        if (volume.equals("primary", ignoreCase = true)) {
            normalizeRelativePathPrefix(path)
        } else {
            removableStorageRelativePath(volume, path)
        }

    fun isPrimaryMediaStoreVolume(volumeName: String?): Boolean {
        val volume = volumeName?.trim().orEmpty()
        if (volume.isEmpty()) return true
        return volume.equals(MediaStorePrimaryVolume, ignoreCase = true) ||
            volume.equals(MediaStoreExternalVolume, ignoreCase = true) ||
            volume.equals("primary", ignoreCase = true)
    }

    fun resolvedMediaStoreVolumeName(
        collectionVolumeName: String?,
        rowVolumeName: String?,
    ): String? {
        val collection = collectionVolumeName?.trim()?.takeIf { it.isNotBlank() }
        if (collection != null && !isPrimaryMediaStoreVolume(collection)) {
            return collection
        }
        return rowVolumeName?.trim()?.takeIf { it.isNotBlank() } ?: collection
    }

    fun mediaStoreRelativePathForVolume(
        volumeName: String?,
        mediaStoreRelativePath: String?,
    ): String? {
        if (isPrimaryMediaStoreVolume(volumeName)) {
            return normalizeRelativePathPrefix(mediaStoreRelativePath)
        }
        return removableStorageRelativePath(volumeName.orEmpty(), mediaStoreRelativePath)
    }

    fun legacyDataRelativePath(
        dataPath: String?,
        primaryStorageRoot: String,
    ): String? {
        val path = dataPath
            ?.replace('\\', '/')
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val parent = path.substringBeforeLast('/', missingDelimiterValue = "").trimEnd('/')
        if (parent.isBlank()) return null
        val primary = primaryStorageRoot.replace('\\', '/').trimEnd('/')
        if (primary.isNotBlank() && (parent == primary || parent.startsWith("$primary/"))) {
            return normalizeRelativePathPrefix(parent.removePrefix(primary).trim('/'))
        }
        val storagePrefix = "/storage/"
        if (parent.startsWith(storagePrefix)) {
            val remainder = parent.removePrefix(storagePrefix)
            val volume = remainder.substringBefore('/')
            val rest = remainder.substringAfter('/', missingDelimiterValue = "")
            if (
                volume.isNotBlank() &&
                !volume.equals("emulated", ignoreCase = true) &&
                !volume.equals("self", ignoreCase = true)
            ) {
                return removableStorageRelativePath(volume, rest)
            }
        }
        return normalizeRelativePathPrefix(parent.trimStart('/'))
    }

    fun removableStorageRelativePath(volumeLabel: String, path: String?): String? {
        val safeVolume = volumeLabel.replace(':', '_').trim().ifBlank { RemovableVolumeFallback }
        val cleanPath = path?.replace('\\', '/')?.trim('/')
        return normalizeRelativePathPrefix(
            listOf("Removable", safeVolume, cleanPath)
                .filter { !it.isNullOrBlank() }
                .joinToString("/"),
        )
    }

    fun shouldScanAllMediaStoreVolumes(sdkInt: Int, relativePathPrefix: String?): Boolean =
        sdkInt >= 29 && relativePathPrefix.isNullOrBlank()

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

    fun mediaStoreSampleRateColumnAvailable(sdkInt: Int): Boolean =
        sdkInt >= MediaStoreSampleRateSdkInt

    fun preferredSampleRateHz(
        mediaStoreSampleRateHz: Int?,
        existingSampleRateHz: Int?,
    ): Int? = mediaStoreSampleRateHz.positiveSampleRate() ?: existingSampleRateHz.positiveSampleRate()

    fun shouldReadSampleRateFromFile(
        readSampleRateEnabled: Boolean,
        knownSampleRateHz: Int?,
    ): Boolean = readSampleRateEnabled && knownSampleRateHz.positiveSampleRate() == null

    fun shouldSkipSampleRateRead(
        lightweight: Boolean,
        storageBusy: Boolean,
    ): Boolean = lightweight || storageBusy

    fun shouldBackfillMissingSampleRates(
        wasLightweight: Boolean,
        isLightweight: Boolean,
    ): Boolean = wasLightweight && !isLightweight

    fun shouldEmitScanProgress(
        scannedCount: Int,
        lastEmittedCount: Int,
        elapsedSinceEmitMs: Long,
        stride: Int = DefaultProgressStride,
        minIntervalMs: Long = DefaultProgressMinIntervalMs,
    ): Boolean {
        if (scannedCount <= 0 || scannedCount == lastEmittedCount) return false
        if (lastEmittedCount <= 0) return true
        if (scannedCount - lastEmittedCount >= stride) return true
        return elapsedSinceEmitMs >= minIntervalMs
    }

    fun shouldRebuildLibrarySummariesIncrementally(
        changedKeyCount: Int,
        existingAlbumSummaryCount: Int,
    ): Boolean {
        if (changedKeyCount <= 0) return false
        if (existingAlbumSummaryCount <= 0) return false
        if (changedKeyCount >= IncrementalSummaryKeyLimit) return false
        return changedKeyCount * IncrementalSummaryFullRebuildRatio < existingAlbumSummaryCount
    }

    const val SafSourceId = "saf"
    const val MediaStoreSampleRateSdkInt = 31
    const val MediaStorePrimaryVolume = "external_primary"
    const val MediaStoreExternalVolume = "external"
    const val RemovableVolumeFallback = "removable"
    const val DefaultProgressStride = 100
    const val DefaultProgressMinIntervalMs = 400L
    const val IncrementalSummaryKeyLimit = 400
    const val IncrementalSummaryFullRebuildRatio = 2
}

data class LibrarySummaryKeySet(
    val albumKeys: Set<String> = emptySet(),
    val artistKeys: Set<String> = emptySet(),
    val folderKeys: Set<String> = emptySet(),
) {
    val changedKeyCount: Int
        get() = albumKeys.size + artistKeys.size + folderKeys.size

    operator fun plus(other: LibrarySummaryKeySet): LibrarySummaryKeySet =
        if (other.changedKeyCount == 0) {
            this
        } else if (changedKeyCount == 0) {
            other
        } else {
            LibrarySummaryKeySet(
                albumKeys = albumKeys + other.albumKeys,
                artistKeys = artistKeys + other.artistKeys,
                folderKeys = folderKeys + other.folderKeys,
            )
        }
}

private fun Int?.positiveSampleRate(): Int? = this?.takeIf { it > 0 }

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
    val hrefParseFailed: Boolean = false,
) {
    val incomplete: Boolean
        get() = hitVisitCap || hrefParseFailed
}
