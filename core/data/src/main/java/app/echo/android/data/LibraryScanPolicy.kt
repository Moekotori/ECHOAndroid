package app.echo.android.data

import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.LibraryScanOptions
import app.echo.android.model.library.LibrarySource
import app.echo.android.model.platform.EchoPlatformCapabilities

data class LibraryScanCompleteness(
    val querySucceeded: Boolean,
    val scannedCount: Int,
    val existingCount: Int,
    val hitVisitCap: Boolean = false,
    val confirmedEmpty: Boolean = false,
)

object LibraryScanPolicy {
    const val MediaStoreNativeIdPrefix = "mediastore:"
    const val MediaStoreFileIdPrefix = "mediastore:file:"
    const val PendingDocumentMetadataFingerprint = "saf:metadata-pending"
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

    fun isMediaStoreFileId(trackId: String): Boolean = trackId.startsWith(MediaStoreFileIdPrefix)

    /** Audio-table id, or the base id of a cue movement. File-fallback ids are not audio rows. */
    fun isMediaStoreAudioTrackId(trackId: String): Boolean {
        val base = CueSheetPolicy.baseTrackId(trackId)
        return base.startsWith(MediaStoreNativeIdPrefix) && !base.startsWith(MediaStoreFileIdPrefix)
    }

    fun isMediaStoreFileTrackId(trackId: String): Boolean =
        CueSheetPolicy.baseTrackId(trackId).startsWith(MediaStoreFileIdPrefix)

    /** Numeric MediaStore `_ID` for an audio or file row, ignoring a `#cue:` suffix. */
    fun mediaStoreNumericId(trackId: String): String? {
        val base = CueSheetPolicy.baseTrackId(trackId)
        val raw = when {
            base.startsWith(MediaStoreFileIdPrefix) -> base.removePrefix(MediaStoreFileIdPrefix)
            base.startsWith(MediaStoreNativeIdPrefix) -> base.removePrefix(MediaStoreNativeIdPrefix)
            else -> return null
        }
        return raw.toLongOrNull()?.toString()
    }

    fun cueChildrenByBase(ids: Iterable<String>): Map<String, List<String>> {
        val grouped = LinkedHashMap<String, MutableList<String>>()
        for (id in ids) {
            if (!CueSheetPolicy.isCueTrackId(id)) continue
            grouped.getOrPut(CueSheetPolicy.baseTrackId(id)) { ArrayList(2) }.add(id)
        }
        return grouped
    }

    /**
     * Ids to mark seen for an unchanged file.
     * Cue content is independent of audio mtime. Re-expand it on every scan, including single-track cues.
     */
    fun rememberUnchangedCueIds(
        trackId: String,
        cueChildIds: Collection<String>,
        hasCueSheet: Boolean,
    ): List<String>? {
        if (hasCueSheet) return null
        if (!hasCueSheet && cueChildIds.isNotEmpty()) return null
        return ArrayList<String>(cueChildIds.size + 1).apply {
            add(trackId)
            addAll(cueChildIds)
        }
    }

    /** Map a SAF id onto the MediaStore row emitted for the same file, including cue movements. */
    fun safDuplicateAliases(safBaseId: String, expandedIds: List<String>): List<Pair<String, String>> {
        if (safBaseId.isBlank() || expandedIds.isEmpty()) return emptyList()
        val cueIds = expandedIds.filter { CueSheetPolicy.isCueTrackId(it) }
        if (cueIds.isEmpty()) return listOf(safBaseId to expandedIds.first())
        val firstCue = cueIds.minWithOrNull(compareBy {
            it.substringAfterLast(CueSheetPolicy.CueSuffix).toIntOrNull() ?: Int.MAX_VALUE
        }) ?: cueIds.first()
        val aliases = ArrayList<Pair<String, String>>(cueIds.size + 1)
        aliases += safBaseId to firstCue
        for (target in cueIds) {
            val number = target.substringAfterLast(CueSheetPolicy.CueSuffix).toIntOrNull() ?: continue
            aliases += CueSheetPolicy.cueTrackId(safBaseId, number) to target
        }
        return aliases
    }

    /**
     * A file that is now stored only as cue movements. The old whole-file row should hand its
     * favorites, playlists and play counts to the first movement before it is removed.
     */
    fun baseRowsSupersededByCue(seenIds: Set<String>, existingIds: Set<String>): List<Pair<String, String>> {
        if (seenIds.isEmpty() || existingIds.isEmpty()) return emptyList()
        return cueChildrenByBase(seenIds).mapNotNull { (base, children) ->
            if (base !in existingIds || base in seenIds) return@mapNotNull null
            val target = children.minWithOrNull(compareBy {
                it.substringAfterLast(CueSheetPolicy.CueSuffix).toIntOrNull() ?: Int.MAX_VALUE
            }) ?: return@mapNotNull null
            base to target
        }
    }

    /**
     * Where an existing SAF row should land after a MediaStore row for the same file is in the library.
     * Cue movements merge onto the same movement number. A whole-file SAF row merges onto the
     * MediaStore file, or onto the first movement when that file is stored only as cue splits.
     */
    fun duplicateMergeTarget(
        sourceId: String,
        nativeBaseId: String,
        liveIds: Set<String>,
        cueChildren: Map<String, List<String>>,
    ): String? {
        if (sourceId.isBlank() || nativeBaseId.isBlank() || sourceId == nativeBaseId) return null
        if (CueSheetPolicy.isCueTrackId(sourceId)) {
            val number = sourceId.substringAfterLast(CueSheetPolicy.CueSuffix).toIntOrNull() ?: return null
            val cueId = CueSheetPolicy.cueTrackId(nativeBaseId, number)
            return cueId.takeIf { it in liveIds }
        }
        if (nativeBaseId in liveIds) return nativeBaseId
        return cueChildren[nativeBaseId]?.minWithOrNull(compareBy {
            it.substringAfterLast(CueSheetPolicy.CueSuffix).toIntOrNull() ?: Int.MAX_VALUE
        })
    }

    /**
     * Folder key shared by audio rows and cue files. Removable volumes use the same
     * `Removable/<volume>/` prefix as track paths; pre-Q uses the file's parent path.
     */
    fun cueSheetFolder(
        volumeName: String?,
        mediaStoreRelativePath: String?,
        legacyDataPath: String?,
        primaryStorageRoot: String,
    ): String {
        val indexed = when {
            mediaStoreRelativePath != null || !volumeName.isNullOrBlank() ->
                mediaStoreRelativePathForVolume(volumeName, mediaStoreRelativePath)
            else -> null
        } ?: legacyDataRelativePath(legacyDataPath, primaryStorageRoot)
        return indexed?.trim('/').orEmpty()
    }

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

    /**
     * 增量探针行的下一步:排除目录不拉全列。
     * 主动扫描不记 seen,完整扫描后从曲库删除;后台自动扫描仍保留已入库行。
     * 时长/大小/格式仍只拦新文件。路径未知时不按目录剪枝,避免 RELATIVE_PATH 缺失时把整卷当成排除根。
     */
    fun classifyMediaStoreProbeRow(
        existing: TrackFingerprint?,
        relativePath: String?,
        dateModifiedSeconds: Long,
        sizeBytes: Long,
        options: LibraryScanOptions,
        rejectedByCache: Boolean,
        fileName: String? = null,
        removeExcludedFromLibrary: Boolean = true,
    ): MediaStoreProbeAction {
        if (!options.includesDirectory(relativePath)) {
            return if (existing != null && !removeExcludedFromLibrary) {
                MediaStoreProbeAction.RememberSeen
            } else {
                MediaStoreProbeAction.SkipRejected
            }
        }
        if (existing == null && fileName != null && !options.acceptsFileFormat(fileName, alreadyImported = false)) {
            return MediaStoreProbeAction.SkipRejected
        }
        if (existing == null && rejectedByCache) return MediaStoreProbeAction.SkipRejected
        if (isMediaStoreRowUnchanged(existing, dateModifiedSeconds, sizeBytes)) {
            return MediaStoreProbeAction.RememberSeen
        }
        return MediaStoreProbeAction.FetchFull
    }

    fun unseenIds(existingIds: Collection<String>, seenIds: Set<String>): List<String> =
        existingIds.distinct().filterNot(seenIds::contains)

    /** 后台扫描只清磁盘上消失的歌;排除目录里已入库的行要等用户主动扫描。 */
    fun isDirectoryCleanupCandidate(
        relativePath: String?,
        options: LibraryScanOptions,
        removeExcludedFromLibrary: Boolean,
    ): Boolean = removeExcludedFromLibrary || options.includesDirectory(relativePath)

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
        EchoPlatformCapabilities.fromSdk(sdkInt).mediaStoreRelativePath &&
            relativePathPrefix.isNullOrBlank()

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
     * 精确快照键，只用于测试和索引构建。真正认同一文件用 [localFileIdentity]：
     * 目录 + 文件名。大小和 mtime 不是同一性。
     */
    fun localFileDuplicateKey(
        relativePath: String?,
        sizeBytes: Long,
        dateModifiedSeconds: Long,
        displayName: String? = null,
    ): String? {
        val identity = localFileIdentity(relativePath, displayName) ?: return null
        if (sizeBytes <= 0L || dateModifiedSeconds <= 0L) return null
        val dir = identity.directory
        val name = identity.name
        return "${dir.length}:$dir${name.length}:$name|$sizeBytes|$dateModifiedSeconds"
    }

    /** 目录和文件名。Unicode 用 NFC，保留大小写，避免在区分大小写的来源上把两首歌并掉。 */
    internal fun localFileIdentity(relativePath: String?, displayName: String?): LocalFileIdentity? {
        val directory = normalizedDuplicateDirectory(relativePath) ?: return null
        val name = normalizedDuplicateName(displayName) ?: return null
        return LocalFileIdentity(directory, name)
    }

    /**
     * 仅当调用方明确打开缓存时，嵌套目录才在 lastModified 未变时跳过列举。
     * 曲库刷新不能用它：很多 SAF 提供者在子文件增删时不改目录 mtime。
     * 根目录、mtime 为 0、或 mtime 变化时必须重列。
     */
    fun shouldReuseCachedDocumentListing(
        cachedLastModifiedMs: Long,
        incomingLastModifiedMs: Long,
        isTreeRoot: Boolean,
    ): Boolean =
        !isTreeRoot &&
            cachedLastModifiedMs > 0L &&
            incomingLastModifiedMs > 0L &&
            cachedLastModifiedMs == incomingLastModifiedMs

    fun shouldReuseUnchangedDocumentTrack(
        existing: TrackFingerprint?,
        incomingContentUri: String,
        incomingSizeBytes: Long,
        incomingDateModifiedSeconds: Long,
        incomingRelativePath: String?,
    ): Boolean {
        val fingerprint = existing?.fingerprint
        if (existing == null || existing.durationMs <= 0L) return false
        if (fingerprint.isNullOrEmpty() || fingerprint == PendingDocumentMetadataFingerprint) return false
        return shouldReuseUnchangedDocumentFingerprint(
            existingContentUri = existing.contentUri,
            incomingContentUri = incomingContentUri,
            existingSizeBytes = existing.sizeBytes,
            incomingSizeBytes = incomingSizeBytes,
            existingDateModifiedSeconds = existing.dateModifiedSeconds,
            incomingDateModifiedSeconds = incomingDateModifiedSeconds,
            existingRelativePath = existing.relativePath,
            incomingRelativePath = incomingRelativePath,
        )
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
            // Cue movements store the file uri plus an #echo-cue fragment. The file itself is unchanged.
            CueSheetPolicy.playbackUri(existingContentUri) == CueSheetPolicy.playbackUri(incomingContentUri) &&
            existingSizeBytes == incomingSizeBytes &&
            existingDateModifiedSeconds == incomingDateModifiedSeconds &&
            // 指纹包含 relativePath:路径归一化(如卷名大小写)后必须走 Update 重写行
            existingRelativePath == incomingRelativePath

    fun mediaStoreSampleRateColumnAvailable(sdkInt: Int): Boolean =
        EchoPlatformCapabilities.fromSdk(sdkInt).mediaStoreSampleRateColumn

    /**
     * ALBUM_ARTIST 是 API 30 才正式进入 MediaStore 音频列的;
     * Android 8~10 的 provider 查询该列可能直接抛 IllegalArgumentException,
     * 旧设备不放进投影,专辑归组回退到 artist。
     */
    fun mediaStoreAlbumArtistColumnAvailable(sdkInt: Int): Boolean =
        EchoPlatformCapabilities.fromSdk(sdkInt).mediaStoreAlbumArtistColumn

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
    const val MediaStoreSampleRateSdkInt = EchoPlatformCapabilities.MediaStoreSampleRateColumnSdk
    const val MediaStoreAlbumArtistSdkInt = EchoPlatformCapabilities.MediaStoreAlbumArtistColumnSdk
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
    val genreKeys: Set<String> = emptySet(),
) {
    val changedKeyCount: Int
        get() = albumKeys.size + artistKeys.size + folderKeys.size + genreKeys.size

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
                genreKeys = genreKeys + other.genreKeys,
            )
        }
}

private fun Int?.positiveSampleRate(): Int? = this?.takeIf { it > 0 }

enum class LibraryScanRowAction {
    Insert,
    Update,
    RememberSeen,
}

enum class MediaStoreProbeAction {
    FetchFull,
    RememberSeen,
    SkipRejected,
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
    val failedReadCount: Int = 0,
    val excludedDirectoryCount: Int = 0,
    val unmatchedCueCount: Int = 0,
    /** 本次见到的整文件 id。同一路径的重复行在清理前合并。 */
    val fileIdentities: LocalFileDuplicateIndex<String> = LocalFileDuplicateIndex.empty(),
)

data class RemoteSyncVisit(
    val visitedCount: Int,
    val hitVisitCap: Boolean,
    val hrefParseFailed: Boolean = false,
) {
    val incomplete: Boolean
        get() = hitVisitCap || hrefParseFailed
}
