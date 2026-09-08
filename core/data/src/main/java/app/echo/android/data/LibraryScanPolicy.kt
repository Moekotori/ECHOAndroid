package app.echo.android.data

import app.echo.android.model.library.LibrarySource

data class LibraryScanCompleteness(
    val querySucceeded: Boolean,
    val scannedCount: Int,
    val existingCount: Int,
    val hitVisitCap: Boolean = false,
    val confirmedEmpty: Boolean = false,
)

object LibraryScanPolicy {
    const val MediaStoreNativeIdPrefix = "mediastore:"
    const val SafTrackIdPrefix = "saf:"
    const val LocalSourceSql = "(source = 'mediastore' OR source = 'saf')"
    const val RemoteSourceSql = "(source != 'mediastore' AND source != 'saf')"

    fun shouldDeleteMissingLibraryRows(completeness: LibraryScanCompleteness): Boolean {
        if (!completeness.querySucceeded) return false
        if (completeness.hitVisitCap) return false
        if (completeness.scannedCount <= 0 && completeness.existingCount > 0 && !completeness.confirmedEmpty) return false
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

    /**
     * 增量扫描的轻量比对:MediaStore 行的 (DATE_MODIFIED, SIZE) 与库内快照一致即视为未变,
     * 跳过全列拉取与指纹重算。文件内容/元数据变更都会改 mtime 或 size;
     * 例外是 MediaStore 重新归组 albumId(artworkUri 变化)不改文件,该情况在文件下次被触碰时补上。
     * 两个字段都为 0 视为快照不可信,退回全量路径。
     */
    fun isMediaStoreRowUnchanged(
        existing: TrackFingerprint?,
        dateModifiedSeconds: Long,
        sizeBytes: Long,
    ): Boolean =
        existing?.fingerprint != null &&
            (dateModifiedSeconds > 0L || sizeBytes > 0L) &&
            existing.dateModifiedSeconds == dateModifiedSeconds &&
            existing.sizeBytes == sizeBytes

    fun unseenIds(existingIds: Collection<String>, seenIds: Set<String>): List<String> =
        existingIds.distinct().filterNot(seenIds::contains)

    fun shouldRefreshLocalLibraryAfterPermissionGrant(localMediaStoreCount: Int): Boolean =
        localMediaStoreCount <= 0

    fun usesDocumentTreeScan(volume: String): Boolean =
        volume.isNotBlank()

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
        // MediaStore 卷名固定小写,SAF documentId 里的卷 UUID 通常大写:
        // 统一小写,否则同一张卡会在文件夹视图里分裂成两个目录树
        val safeVolume = volumeLabel.replace(':', '_').trim().lowercase().ifBlank { RemovableVolumeFallback }
        val cleanPath = path?.replace('\\', '/')?.trim('/')
        return normalizeRelativePathPrefix(
            listOf("Removable", safeVolume, cleanPath)
                .filter { !it.isNullOrBlank() }
                .joinToString("/"),
        )
    }

    fun shouldScanAllMediaStoreVolumes(sdkInt: Int, relativePathPrefix: String?): Boolean =
        sdkInt >= 29 && relativePathPrefix.isNullOrBlank()

    /** 一个 MediaStore 集合(卷)对应的库内行范围,用于把删除限定在本次真正扫过的卷。 */
    fun mediaStoreVolumeScope(volumeName: String?): MediaStoreVolumeScope {
        val volume = volumeName?.trim().orEmpty()
        return when {
            // Q 之前的单一 external 集合、Q+ 的合并 external 视图:覆盖所有卷
            volume.isEmpty() || volume.equals(MediaStoreExternalVolume, ignoreCase = true) ->
                MediaStoreVolumeScope.AllVolumes
            isPrimaryMediaStoreVolume(volume) -> MediaStoreVolumeScope.PrimaryVolume
            else -> MediaStoreVolumeScope.RemovableVolume(
                removableStorageRelativePath(volume, null) ?: RemovableRootPrefix,
            )
        }
    }

    fun mediaStoreRowWithinVolumeScopes(
        relativePath: String?,
        scopes: Collection<MediaStoreVolumeScope>,
    ): Boolean = scopes.any { scope ->
        when (scope) {
            MediaStoreVolumeScope.AllVolumes -> true
            MediaStoreVolumeScope.PrimaryVolume ->
                relativePath == null || !relativePath.startsWith(RemovableRootPrefix, ignoreCase = true)
            is MediaStoreVolumeScope.RemovableVolume ->
                relativePath?.startsWith(scope.relativePathPrefix, ignoreCase = true) == true
        }
    }

    /**
     * 本地文件跨来源(mediastore/saf)的同一性钥匙:目录 + 文件名 + 大小 + mtime。
     * 任一字段不可信(空/0)时返回 null,表示放弃去重判定。
     */
    fun localFileDuplicateKey(
        relativePath: String?,
        sizeBytes: Long,
        dateModifiedSeconds: Long,
        displayName: String? = null,
    ): String? {
        val dir = relativePath?.replace('\\', '/')?.trim('/')?.takeIf { it.isNotBlank() } ?: return null
        if (sizeBytes <= 0L || dateModifiedSeconds <= 0L) return null
        val name = displayName?.takeIf { it.isNotBlank() } ?: return null
        // 保留路径和文件名大小写，避免在区分大小写的 provider 上误合并。
        return "${dir.length}:$dir${name.length}:$name|$sizeBytes|$dateModifiedSeconds"
    }

    fun shouldReuseUnchangedDocumentFingerprint(
        existingContentUri: String,
        incomingContentUri: String,
        existingSizeBytes: Long,
        incomingSizeBytes: Long,
        existingDateModifiedSeconds: Long,
        incomingDateModifiedSeconds: Long,
        existingRelativePath: String? = null,
        incomingRelativePath: String? = null,
    ): Boolean =
        existingContentUri.isNotBlank() &&
            existingContentUri == incomingContentUri &&
            existingSizeBytes == incomingSizeBytes &&
            existingDateModifiedSeconds == incomingDateModifiedSeconds &&
            // 指纹包含 relativePath:路径归一化(如卷名大小写)后必须走 Update 重写行
            existingRelativePath == incomingRelativePath

    fun mediaStoreSampleRateColumnAvailable(sdkInt: Int): Boolean =
        sdkInt >= MediaStoreSampleRateSdkInt

    /**
     * ALBUM_ARTIST 是 API 30 才正式进入 MediaStore 音频列的;
     * Android 8~10 的 provider 查询该列可能直接抛 IllegalArgumentException,
     * 旧设备不放进投影,专辑归组回退到 artist。
     */
    fun mediaStoreAlbumArtistColumnAvailable(sdkInt: Int): Boolean =
        sdkInt >= MediaStoreAlbumArtistSdkInt

    fun isUnsupportedMediaStoreSampleRateColumn(error: Throwable): Boolean {
        if (error !is IllegalArgumentException) return false
        val message = error.message.orEmpty()
        return message.contains("sample_rate", ignoreCase = true)
    }

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
    const val MediaStoreAlbumArtistSdkInt = 30
    const val MediaStorePrimaryVolume = "external_primary"
    const val MediaStoreExternalVolume = "external"
    const val RemovableVolumeFallback = "removable"
    const val RemovableRootPrefix = "Removable/"
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

/** MediaStore 卷在删除判定里的行范围:只有本次完整扫过的卷才允许删其缺失行。 */
sealed interface MediaStoreVolumeScope {
    /** 覆盖所有卷:Q 之前的单一 external 集合,或 Q+ 的合并 external 视图 */
    data object AllVolumes : MediaStoreVolumeScope

    /** 主卷(内置存储):relativePath 不带 Removable/ 前缀的行 */
    data object PrimaryVolume : MediaStoreVolumeScope

    /** 单个可移动卷:relativePath 以 Removable/<卷名>/ 开头的行 */
    data class RemovableVolume(val relativePathPrefix: String) : MediaStoreVolumeScope
}

data class MediaStoreScanOutcome(
    val scannedCount: Int,
    val querySucceeded: Boolean,
    /** 本次所有查询都成功的卷。查询失败/游标为 null 的卷不在列,其行不得被当作缺失删除。 */
    val completeVolumeScopes: List<MediaStoreVolumeScope> = emptyList(),
)

data class RemoteSyncVisit(
    val visitedCount: Int,
    val hitVisitCap: Boolean,
    val hrefParseFailed: Boolean = false,
) {
    val incomplete: Boolean
        get() = hitVisitCap || hrefParseFailed
}
