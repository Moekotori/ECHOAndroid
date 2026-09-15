package app.echo.android.data

import app.echo.android.model.library.CueSheet
import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.LibraryScanOptions
import app.echo.android.model.platform.EchoPlatformCapabilities
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class MediaStoreTrackScanner(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver
    internal val rejectedFileCache by lazy { LocalScanFilterCache(java.io.File(appContext.cacheDir, "scan-rejected-v1.json")) }
    private var includeSampleRateColumn =
        LibraryScanPolicy.mediaStoreSampleRateColumnAvailable(Build.VERSION.SDK_INT)
    private var includeChangeProbePathColumns = true

    suspend fun scanAudio(
        batchSize: Int = DefaultBatchSize,
        relativePathPrefix: String? = null,
        existingTracks: Map<String, TrackFingerprint> = emptyMap(),
        readSampleRate: Boolean = true,
        options: LibraryScanOptions = LibraryScanOptions(0L, 0L, false, false),
        rejectedFiles: LocalScanFilterCache? = null,
        onSkipped: suspend () -> Unit = {},
        onTotalCount: suspend (Int?) -> Unit = {},
        onUnchangedIds: suspend (List<String>) -> Unit = {},
        removeExcludedFromLibrary: Boolean = true,
        onBatch: suspend (List<LibraryTrackEntity>) -> Unit,
        onProgress: suspend (scannedCount: Int, currentTrack: LibraryTrackEntity?) -> Unit,
    ): MediaStoreScanOutcome {
        val normalizedRelativePath = normalizeRelativePathPrefix(relativePathPrefix)
        val collections = audioCollections(normalizedRelativePath)
        if (collections.isEmpty()) {
            return MediaStoreScanOutcome(scannedCount = 0, querySucceeded = false)
        }
        val (selection, selectionArgs) = audioSelection(normalizedRelativePath)
        val safeBatchSize = batchSize.coerceAtLeast(1)
        val cueSheets = loadCueSheets()
        val batch = ArrayList<LibraryTrackEntity>(safeBatchSize)
        var scannedCount = 0
        var estimatedTotal = 0
        var querySucceeded = false

        suspend fun flushBatch() {
            if (batch.isEmpty()) return
            onBatch(batch.toList())
            batch.clear()
        }

        suspend fun consumeFullListing(listing: Cursor, collection: MediaStoreCollection): Boolean {
            var complete = true
            val columns = MediaStoreColumns.from(listing)
            while (listing.moveToNext()) {
                coroutineContext.ensureActive()
                val parsed = runCatching {
                    val row = listing.toAudioRow(collection, columns)
                    if (!options.includesDirectory(row.relativePath)) {
                        val trackId = row.libraryId()
                        if (existingTracks[trackId] != null && !removeExcludedFromLibrary) {
                            onUnchangedIds(listOf(trackId))
                            scannedCount++
                            onProgress(scannedCount, null)
                        } else {
                            scannedCount++
                            onSkipped()
                            onProgress(scannedCount, null)
                        }
                        null
                    } else if (!options.acceptsFileFormat(row.fileName, row.libraryId() in existingTracks)) {
                        scannedCount++
                        onSkipped()
                        onProgress(scannedCount, null)
                        null
                    } else if (existingTracks[row.libraryId()] == null && !options.accepts(row.durationMs, row.sizeBytes, null)) {
                        rejectedFiles?.remember(row.contentUri, row.sizeBytes, row.dateModifiedSeconds, row.durationMs)
                        scannedCount++
                        onSkipped()
                        onProgress(scannedCount, null)
                        null
                    } else row.toTrackEntity(existingTracks, readSampleRate) to row.fileName
                }.onFailure { error ->
                    complete = false
                    Log.w(TAG, "Skipping unreadable MediaStore audio row.", error)
                }.getOrNull() ?: continue
                val (track, fileName) = parsed
                val expanded = expandCueTracks(track, fileName, cueSheets)
                expanded.forEach { item ->
                    batch += item
                    scannedCount += 1
                    onProgress(scannedCount, item)
                    if (batch.size >= safeBatchSize) {
                        flushBatch()
                    }
                }
            }
            return complete
        }

        suspend fun scanFallbackFiles(
            collection: MediaStoreCollection,
            volumeScope: MediaStoreVolumeScope,
            relativePathPrefix: String?,
        ) {
            val filesUri = fallbackFilesUri(collection.volumeName) ?: return
            val (selection, selectionArgs) = fallbackFilesSelection(relativePathPrefix)
            val cursor = runCatching {
                contentResolver.query(filesUri, fallbackFilesProjection(), selection, selectionArgs, null)
            }.getOrNull()
            if (cursor == null) {
                val keep = existingTracks.values.mapNotNull { fingerprint ->
                    fingerprint.id.takeIf { id ->
                        LibraryScanPolicy.isMediaStoreFileId(id) &&
                            LibraryScanPolicy.mediaStoreRowWithinVolumeScopes(
                                relativePath = fingerprint.relativePath,
                                scopes = listOf(volumeScope),
                            )
                    }
                }
                if (keep.isNotEmpty()) onUnchangedIds(keep)
                return
            }
            cursor.use { listing ->
                estimatedTotal += listing.count.coerceAtLeast(0)
                onTotalCount(estimatedTotal.takeIf { it > 0 })
                val idIndex = listing.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameIndex = listing.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeIndex = listing.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeIndex = listing.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val modifiedIndex = listing.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val pathIndex = listing.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                while (listing.moveToNext()) {
                    coroutineContext.ensureActive()
                    val name = listing.getStringOrNull(nameIndex) ?: continue
                    val mime = if (mimeIndex >= 0) listing.getStringOrNull(mimeIndex) else null
                    if (!LocalAudioFileTypes.isSupported(name, mime)) continue
                    val mediaId = listing.getLong(idIndex)
                    val row = MediaStoreAudioRow(
                        mediaId = mediaId,
                        fileName = name,
                        contentUri = Uri.withAppendedPath(filesUri, mediaId.toString()).toString(),
                        title = name.substringBeforeLast('.', name),
                        artist = canonicalUnknownArtist(),
                        album = null,
                        albumArtist = null,
                        albumId = null,
                        durationMs = 0L,
                        trackNumber = null,
                        discNumber = null,
                        year = null,
                        mimeType = LocalAudioFileTypes.resolvedMimeType(name, mime),
                        sizeBytes = if (sizeIndex >= 0) listing.getLongOrNull(sizeIndex) ?: 0L else 0L,
                        sampleRateHz = null,
                        dateModifiedSeconds = if (modifiedIndex >= 0) listing.getLongOrNull(modifiedIndex) ?: 0L else 0L,
                        relativePath = if (pathIndex >= 0) {
                            LibraryScanPolicy.mediaStoreRelativePathForVolume(
                                volumeName = collection.volumeName,
                                mediaStoreRelativePath = listing.getStringOrNull(pathIndex),
                            )
                        } else {
                            null
                        },
                        idPrefix = LibraryScanPolicy.MediaStoreFileIdPrefix,
                    )
                    val parsed = runCatching {
                        if (!options.includesDirectory(row.relativePath)) {
                            val trackId = row.libraryId()
                            if (existingTracks[trackId] != null && !removeExcludedFromLibrary) {
                                onUnchangedIds(listOf(trackId))
                                scannedCount++
                                onProgress(scannedCount, null)
                            } else {
                                scannedCount++
                                onSkipped()
                                onProgress(scannedCount, null)
                            }
                            null
                        } else if (!options.acceptsFileFormat(row.fileName, row.libraryId() in existingTracks)) {
                            scannedCount++
                            onSkipped()
                            onProgress(scannedCount, null)
                            null
                        } else if (existingTracks[row.libraryId()] == null &&
                            !options.accepts(row.durationMs, row.sizeBytes, null)
                        ) {
                            rejectedFiles?.remember(
                                row.contentUri,
                                row.sizeBytes,
                                row.dateModifiedSeconds,
                                row.durationMs,
                            )
                            scannedCount++
                            onSkipped()
                            onProgress(scannedCount, null)
                            null
                        } else {
                            row.toTrackEntity(existingTracks, readSampleRate)
                        }
                    }.onFailure { error ->
                        Log.w(TAG, "Skipping unreadable MediaStore file audio row.", error)
                    }.getOrNull() ?: continue
                    batch += parsed
                    scannedCount += 1
                    onProgress(scannedCount, parsed)
                    if (batch.size >= safeBatchSize) flushBatch()
                }
            }
        }

        // 只有全部查询成功的卷才算"完整扫过":null 游标(卷 provider 短暂不可用等)
        // 会让该卷缺席 seenIds,若仍计入完整卷,清理阶段就会把整卷曲目误删
        val completeVolumeScopes = ArrayList<MediaStoreVolumeScope>()
        for (collection in collections) {
            coroutineContext.ensureActive()
            val volumeScope = LibraryScanPolicy.mediaStoreVolumeScope(collection.volumeName)
            if (existingTracks.isEmpty() && rejectedFiles?.hasEntries() != true) {
                // 首扫:直接全列拉取
                val cursor = queryAudioListing(collection, selection, selectionArgs) ?: continue
                querySucceeded = true
                val complete = cursor.use { listing ->
                    estimatedTotal += listing.count.coerceAtLeast(0)
                    onTotalCount(estimatedTotal.takeIf { it > 0 })
                    consumeFullListing(listing, collection)
                }
                if (complete) completeVolumeScopes += volumeScope
                scanFallbackFiles(collection, volumeScope, relativePathPrefix)
                continue
            }
            // 增量:先用 _ID/DATE_MODIFIED/SIZE 轻量游标与库内快照比对,
            // 带上路径列以便排除目录在探针阶段剪枝,不必再拉全列。
            // 未变行只上报 id(供删除检测),只有变化/新增行才做全列拉取和指纹重算。
            val probe = queryChangeProbe(collection, selection, selectionArgs) ?: continue
            querySucceeded = true
            val changedMediaIds = ArrayList<Long>()
            probe.use { listing ->
                estimatedTotal += listing.count.coerceAtLeast(0)
                onTotalCount(estimatedTotal.takeIf { it > 0 })
                val idIndex = listing.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val modifiedIndex = listing.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val sizeIndex = listing.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val relativePathIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    listing.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else {
                    -1
                }
                val volumeNameIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    listing.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)
                } else {
                    -1
                }
                @Suppress("DEPRECATION")
                val dataIndex = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    listing.getColumnIndex(MediaStore.Audio.Media.DATA)
                } else {
                    -1
                }
                val displayNameIndex = listing.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val unchangedIds = ArrayList<String>()
                while (listing.moveToNext()) {
                    coroutineContext.ensureActive()
                    val mediaId = listing.getLong(idIndex)
                    val trackId = "${LibraryScanPolicy.MediaStoreNativeIdPrefix}$mediaId"
                    val cueIds = existingTracks.keys.filter { id ->
                        CueSheetPolicy.isCueTrackId(id) && CueSheetPolicy.baseTrackId(id) == trackId
                    }
                    val sizeBytes = listing.getLongOrNull(sizeIndex) ?: 0L
                    val dateModifiedSeconds = listing.getLongOrNull(modifiedIndex) ?: 0L
                    val rejectedByCache = existingTracks[trackId] == null && rejectedFiles?.shouldSkip(
                        Uri.withAppendedPath(collection.uri, mediaId.toString()).toString(),
                        sizeBytes, dateModifiedSeconds, options,
                    ) == true
                    val fileName = if (displayNameIndex >= 0) listing.getStringOrNull(displayNameIndex) else null
                    val relativePath = listing.probeRelativePath(
                        collectionVolumeName = collection.volumeName,
                        relativePathIndex = relativePathIndex,
                        volumeNameIndex = volumeNameIndex,
                        dataIndex = dataIndex,
                    )
                    val cueChanged = cueIds.isNotEmpty() || hasCueSheet(relativePath, fileName, cueSheets)
                    when (
                        LibraryScanPolicy.classifyMediaStoreProbeRow(
                            existing = existingTracks[trackId] ?: cueIds.firstOrNull()?.let(existingTracks::get),
                            relativePath = relativePath,
                            dateModifiedSeconds = dateModifiedSeconds,
                            sizeBytes = sizeBytes,
                            options = options,
                            rejectedByCache = rejectedByCache,
                            fileName = fileName,
                            removeExcludedFromLibrary = removeExcludedFromLibrary,
                        )
                    ) {
                        MediaStoreProbeAction.SkipRejected -> {
                            scannedCount++
                            onSkipped()
                        }
                        MediaStoreProbeAction.RememberSeen -> {
                            if (cueChanged) {
                                changedMediaIds += mediaId
                            } else {
                                unchangedIds += trackId
                                scannedCount += 1
                            }
                        }
                        MediaStoreProbeAction.FetchFull -> changedMediaIds += mediaId
                    }
                }
                if (unchangedIds.isNotEmpty()) {
                    onUnchangedIds(unchangedIds)
                    onProgress(scannedCount, null)
                }
            }
            var volumeComplete = true
            for (chunk in changedMediaIds.chunked(IdChunkSize)) {
                coroutineContext.ensureActive()
                val placeholders = chunk.joinToString(",") { "?" }
                val chunkSelection =
                    "$selection AND ${MediaStore.Audio.Media._ID} IN ($placeholders)"
                val chunkArgs = (selectionArgs ?: emptyArray()) +
                    chunk.map(Long::toString)
                val cursor = queryAudioListing(collection, chunkSelection, chunkArgs)
                if (cursor == null) {
                    volumeComplete = false
                    continue
                }
                if (!cursor.use { listing -> consumeFullListing(listing, collection) }) {
                    volumeComplete = false
                }
            }
            if (volumeComplete) {
                completeVolumeScopes += volumeScope
            }
            scanFallbackFiles(collection, volumeScope, relativePathPrefix)
        }
        if (!querySucceeded) {
            return MediaStoreScanOutcome(scannedCount = 0, querySucceeded = false)
        }
        flushBatch()
        onProgress(scannedCount, null)
        return MediaStoreScanOutcome(
            scannedCount = scannedCount,
            querySucceeded = completeVolumeScopes.size == collections.size,
            completeVolumeScopes = completeVolumeScopes,
            failedReadCount = collections.size - completeVolumeScopes.size,
        )
    }

    /** Resolve actual filenames and paths from MediaStore; titles are editable tags, not filenames. */
    suspend fun documentTreeDuplicateKeys(
        existing: Collection<TrackFingerprint>,
        onUnresolvedIds: (List<String>) -> Unit = {},
    ): Map<String, LibraryTrackEntity> {
        val keys = mutableMapOf<String, LibraryTrackEntity>()
        val fingerprints = existing.associateBy { it.id }
        for ((collectionUri, tracks) in existing.filter { LibraryScanPolicy.isMediaStoreNativeId(it.id) }
            .groupBy { it.contentUri.substringBeforeLast('/') }) {
            val collection = MediaStoreCollection(Uri.parse(collectionUri), null)
            for (chunk in tracks.chunked(IdChunkSize)) {
                coroutineContext.ensureActive()
                try {
                    val args = chunk.map { it.contentUri.substringAfterLast('/') }.toTypedArray()
                    val cursor = contentResolver.query(
                        collection.uri, projection().filterNot { it == SampleRateColumn }.toTypedArray(),
                        "${MediaStore.Audio.Media._ID} IN (${args.joinToString(",") { "?" }})",
                        args, null,
                    )
                    if (cursor == null) {
                        onUnresolvedIds(chunk.map { it.id })
                        continue
                    }
                    cursor.use {
                        val columns = MediaStoreColumns.from(cursor)
                        val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                        while (cursor.moveToNext()) {
                            coroutineContext.ensureActive()
                            val row = cursor.toAudioRow(collection, columns)
                            val key = LibraryScanPolicy.localFileDuplicateKey(
                                row.relativePath, row.sizeBytes, row.dateModifiedSeconds,
                                cursor.getStringOrNull(nameIndex),
                            )
                            if (key == null) {
                                onUnresolvedIds(listOf("mediastore:${row.mediaId}"))
                                continue
                            }
                            keys[key] = row.toTrackEntity(fingerprints, readSampleRate = false)
                        }
                    }
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: RuntimeException) {
                    // SAF access is sufficient for a folder scan; uncertain identity never authorizes deduplication.
                    onUnresolvedIds(chunk.map { it.id })
                    Log.w(TAG, "Cannot resolve MediaStore file identities; retaining SAF files.", error)
                }
            }
        }
        return keys
    }

    private fun Cursor.toAudioRow(
        collection: MediaStoreCollection,
        columns: MediaStoreColumns,
    ): MediaStoreAudioRow {
        val mediaId = getLong(columns.idIndex)
        val rawTrack = getLongOrNull(columns.trackIndex)?.toInt()
        val albumId = getLongOrNull(columns.albumIdIndex)?.takeIf { it > 0L }
        return MediaStoreAudioRow(
            mediaId = mediaId,
            contentUri = Uri.withAppendedPath(collection.uri, mediaId.toString()).toString(),
            fileName = columns.fileNameIndex?.let { getStringOrNull(it) },
            title = getStringOrNull(columns.titleIndex).takeUnlessUnknownMetadata() ?: UnknownTrackTitle,
            artist = getStringOrNull(columns.artistIndex).takeUnlessUnknownMetadata() ?: canonicalUnknownArtist(),
            album = getStringOrNull(columns.albumIndex).takeUnlessUnknownMetadata(),
            albumArtist = columns.albumArtistIndex
                ?.let { getStringOrNull(it) }
                .takeUnlessUnknownMetadata(),
            albumId = albumId,
            durationMs = getLongOrNull(columns.durationIndex) ?: 0L,
            trackNumber = rawTrack?.rem(1000)?.takeIf { it > 0 },
            discNumber = rawTrack?.div(1000)?.takeIf { it > 0 },
            year = getLongOrNull(columns.yearIndex)?.toInt()?.takeIf { it > 0 },
            mimeType = getStringOrNull(columns.mimeIndex),
            sizeBytes = getLongOrNull(columns.sizeIndex) ?: 0L,
            sampleRateHz = columns.sampleRateIndex?.let { index ->
                getIntOrNull(index)?.takeIf { it > 0 }
            },
            dateModifiedSeconds = getLongOrNull(columns.modifiedIndex) ?: 0L,
            relativePath = relativePath(collection.volumeName, columns),
            genre = columns.genreIndex?.let { getStringOrNull(it) }?.takeIf { it.isNotBlank() },
            composer = columns.composerIndex?.let { getStringOrNull(it) }?.takeIf { it.isNotBlank() },
        )
    }

    private fun MediaStoreAudioRow.toTrackEntity(
        existingTracks: Map<String, TrackFingerprint>,
        readSampleRate: Boolean,
    ): LibraryTrackEntity {
        val trackId = libraryId()
        val existingTrack = existingTracks[trackId]
        val entity = LibraryTrackEntity(
            id = trackId,
            contentUri = contentUri,
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            artworkUri = albumId?.let { "content://media/external/audio/albumart/$it" },
            durationMs = durationMs,
            trackNumber = trackNumber,
            discNumber = discNumber,
            year = year,
            mimeType = mimeType,
            genre = genre,
            composer = composer,
            sizeBytes = sizeBytes,
            sampleRateHz = LibraryScanPolicy.preferredSampleRateHz(sampleRateHz, existingTrack?.sampleRateHz),
            dateModifiedSeconds = dateModifiedSeconds,
            relativePath = relativePath,
        ).withFingerprint()
        if (EchoDsdMetadata.isDsd(mimeType, relativePath) || EchoDsdMetadata.isDsd(mimeType, contentUri)) {
            val dsd = runCatching {
                contentResolver.openInputStream(Uri.parse(contentUri))?.use(EchoDsdMetadata::read)
            }.onFailure { error ->
                Log.d(TAG, "Unable to read DSD metadata for $contentUri.", error)
            }.getOrNull()
            if (dsd != null) {
                return entity.copy(
                    durationMs = dsd.durationMs.takeIf { it > 0L } ?: durationMs,
                    sampleRateHz = dsd.sampleRateHz.takeIf { it > 0 } ?: sampleRateHz,
                ).withAudioTags(dsd.tags).withFingerprint()
            }
        }
        val tagged = if (LibraryWavTagPolicy.isWavContainer(mimeType, contentUri)) {
            entity.withAudioTags(readAudioTagsFromUri(contentUri)).withFingerprint()
        } else {
            entity
        }
        return tagged.withFastPathSampleRate(existingTrack, readSampleRate, ::readSampleRateHz)
    }

    private fun expandCueTracks(
        track: LibraryTrackEntity,
        fileName: String?,
        cueSheets: Map<String, CueSheet>,
    ): List<LibraryTrackEntity> {
        val names = listOfNotNull(fileName)
        val folder = track.relativePath.orEmpty().trim('/')
        val sheet = names.firstNotNullOfOrNull { name ->
            cueSheets[cueFolderKey(folder, name)]
                ?: cueSheets[cueFolderKey(folder, name.substringBeforeLast('.', name))]
        } ?: return listOf(track)
        val matched = CueSheetPolicy.matchAudioName(sheet.fileName, names)
            ?: CueSheetPolicy.matchAudioName(fileName, names)
            ?: names.firstOrNull { name -> CueSheetPolicy.tracksForAudio(sheet, name).isNotEmpty() }
        if (matched == null) return listOf(track)
        return track.splitByCue(sheet, matched)
    }

    private fun hasCueSheet(relativePath: String?, fileName: String?, cueSheets: Map<String, CueSheet>): Boolean {
        val name = fileName?.takeIf { it.isNotBlank() } ?: return false
        val folder = relativePath.orEmpty().replace('\\', '/').trim('/')
        return cueFolderKey(folder, name) in cueSheets ||
            cueFolderKey(folder, name.substringBeforeLast('.', name)) in cueSheets
    }

    private fun cueFolderKey(folder: String, name: String): String =
        "${folder.lowercase()}\u0000${name.lowercase()}"

    private fun loadCueSheets(): Map<String, CueSheet> {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
        ) + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(MediaStore.Files.FileColumns.RELATIVE_PATH)
        } else {
            emptyArray()
        }
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        val cursor = runCatching {
            contentResolver.query(
                uri,
                projection,
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("%.cue"),
                null,
            )
        }.getOrNull() ?: return emptyMap()
        val sheets = LinkedHashMap<String, CueSheet>()
        cursor.use { listing ->
            val idIndex = listing.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameIndex = listing.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeIndex = listing.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
            val pathIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                listing.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
            } else {
                -1
            }
            while (listing.moveToNext()) {
                val name = listing.getStringOrNull(nameIndex) ?: continue
                if (!LocalAudioFileTypes.isCueSheet(name, null)) continue
                val size = if (sizeIndex >= 0) listing.getLongOrNull(sizeIndex) ?: 0L else 0L
                if (size > CueSheetPolicy.MaxCueBytes) continue
                val id = listing.getLong(idIndex)
                val folder = if (pathIndex >= 0) listing.getStringOrNull(pathIndex).orEmpty().trim('/') else ""
                val document = Uri.withAppendedPath(uri, id.toString())
                val sheet = runCatching {
                    contentResolver.openInputStream(document)?.use { input ->
                        val bytes = input.readBytes()
                        if (bytes.size > CueSheetPolicy.MaxCueBytes) null else CueSheetParser.parse(bytes)
                    }
                }.getOrNull() ?: continue
                val indexNames = buildList {
                    add(name)
                    add(name.substringBeforeLast('.', name))
                    sheet.fileName?.substringAfterLast('/')?.let(::add)
                    sheet.tracks.forEach { track ->
                        track.fileName?.substringAfterLast('/')?.let(::add)
                    }
                }.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
                indexNames.forEach { indexName ->
                    sheets[cueFolderKey(folder, indexName)] = sheet
                    sheets[cueFolderKey(folder, indexName.substringBeforeLast('.', indexName))] = sheet
                }
            }
        }
        return sheets
    }

    internal fun readAudioTagsFromUri(contentUri: String): AudioTagFields? =
        runCatching {
            contentResolver.openInputStream(Uri.parse(contentUri))?.use { input ->
                readLocalAudioTags(input)
            }
        }.onFailure { error ->
            Log.d(TAG, "Unable to read audio tags for $contentUri.", error)
        }.getOrNull()

    internal fun isWavTagBackfillComplete(): Boolean = wavTagBackfillMarker().exists()

    internal fun markWavTagBackfillComplete() {
        runCatching { wavTagBackfillMarker().writeText("1") }
    }

    internal fun isAggregationKeyBackfillComplete(): Boolean = aggregationKeyBackfillMarker().exists()

    internal fun markAggregationKeyBackfillComplete() {
        runCatching { aggregationKeyBackfillMarker().writeText("1") }
    }

    private fun wavTagBackfillMarker(): java.io.File =
        java.io.File(appContext.filesDir, "wav-tag-backfill-v1")

    private fun aggregationKeyBackfillMarker(): java.io.File =
        java.io.File(appContext.filesDir, "library-aggregation-keys-v2")

    internal fun readSampleRateHz(contentUri: String): Int? =
        runCatching {
            if (Build.VERSION.SDK_INT < EchoPlatformCapabilities.MediaMetadataSampleRateSdk) return@runCatching null
            val uri = Uri.parse(contentUri)
            val retriever = MediaMetadataRetriever()
            try {
                contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    retriever.setDataSource(descriptor.fileDescriptor)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                        ?.toIntOrNull()
                        ?.takeIf { it > 0 }
                }
            } finally {
                retriever.release()
            }
        }.onFailure { error ->
            Log.d(TAG, "Unable to read audio sample rate for $contentUri.", error)
        }.getOrNull()

    private fun Cursor.relativePath(
        collectionVolumeName: String?,
        columns: MediaStoreColumns,
    ): String? {
        val rowVolumeName = columns.volumeNameIndex?.let { index ->
            getStringOrNull(index)
        }
        val volumeName = LibraryScanPolicy.resolvedMediaStoreVolumeName(
            collectionVolumeName = collectionVolumeName,
            rowVolumeName = rowVolumeName,
        )
        return when {
            columns.relativePathIndex != null -> LibraryScanPolicy.mediaStoreRelativePathForVolume(
                volumeName = volumeName,
                mediaStoreRelativePath = getStringOrNull(columns.relativePathIndex),
            )
            columns.dataIndex != null -> {
                @Suppress("DEPRECATION")
                val storageRoot = Environment.getExternalStorageDirectory()
                    .absolutePath
                    .replace('\\', '/')
                    .trimEnd('/')
                LibraryScanPolicy.legacyDataRelativePath(
                    dataPath = getStringOrNull(columns.dataIndex),
                    primaryStorageRoot = storageRoot,
                )
            }
            else -> LibraryScanPolicy.mediaStoreRelativePathForVolume(volumeName, null)
        }
    }

    private fun audioCollections(relativePathPrefix: String?): List<MediaStoreCollection> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return listOf(
                MediaStoreCollection(
                    uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    volumeName = null,
                ),
            )
        }
        val volumeNames = runCatching { MediaStore.getExternalVolumeNames(appContext) }
            .getOrDefault(emptySet())
        val selectedNames = if (
            LibraryScanPolicy.shouldScanAllMediaStoreVolumes(Build.VERSION.SDK_INT, relativePathPrefix)
        ) {
            volumeNames.ifEmpty { listOf(MediaStore.VOLUME_EXTERNAL) }
        } else {
            volumeNames.filter(LibraryScanPolicy::isPrimaryMediaStoreVolume)
                .ifEmpty { listOf(MediaStore.VOLUME_EXTERNAL_PRIMARY) }
        }
        return selectedNames.map { volumeName ->
            MediaStoreCollection(
                uri = MediaStore.Audio.Media.getContentUri(volumeName),
                volumeName = volumeName,
            )
        }
    }

    private data class MediaStoreColumns(
        val idIndex: Int,
        val titleIndex: Int,
        val fileNameIndex: Int?,
        val artistIndex: Int,
        val albumIndex: Int,
        val albumArtistIndex: Int?,
        val albumIdIndex: Int,
        val durationIndex: Int,
        val trackIndex: Int,
        val yearIndex: Int,
        val mimeIndex: Int,
        val sizeIndex: Int,
        val modifiedIndex: Int,
        val relativePathIndex: Int?,
        val dataIndex: Int?,
        val sampleRateIndex: Int?,
        val volumeNameIndex: Int?,
        val genreIndex: Int?,
        val composerIndex: Int?,
    ) {
        companion object {
            fun from(cursor: Cursor): MediaStoreColumns =
                MediaStoreColumns(
                    idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID),
                    titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE),
                    fileNameIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME).takeIf { it >= 0 },
                    artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST),
                    albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM),
                    // API 30 之前不在投影里;个别 OEM provider 也可能不返回,统一容错
                    albumArtistIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                        .takeIf { it >= 0 },
                    albumIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID),
                    durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION),
                    trackIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK),
                    yearIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR),
                    mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE),
                    sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE),
                    modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED),
                    relativePathIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH).takeIf { it >= 0 }
                    } else {
                        null
                    },
                    dataIndex = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        @Suppress("DEPRECATION")
                        cursor.getColumnIndex(MediaStore.Audio.Media.DATA).takeIf { it >= 0 }
                    } else {
                        null
                    },
                    sampleRateIndex = if (
                        LibraryScanPolicy.mediaStoreSampleRateColumnAvailable(Build.VERSION.SDK_INT)
                    ) {
                        cursor.getColumnIndex(SampleRateColumn).takeIf { it >= 0 }
                    } else {
                        null
                    },
                    volumeNameIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME).takeIf { it >= 0 }
                    } else {
                        null
                    },
                    genreIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        cursor.getColumnIndex(MediaStore.Audio.Media.GENRE).takeIf { it >= 0 }
                    } else {
                        null
                    },
                    composerIndex = cursor.getColumnIndex(MediaStore.Audio.Media.COMPOSER).takeIf { it >= 0 },
                )
        }
    }

    private fun audioSelection(relativePathPrefix: String?): Pair<String, Array<String>?> {
        val (nameSql, nameArgs) = LocalAudioFileTypes.mediaStoreFallbackNameClause(
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val musicSelection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR $nameSql)"
        if (relativePathPrefix == null) return musicSelection to nameArgs

        val escapedPrefix = "${escapeSqlLikeArgument(relativePathPrefix)}%"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "$musicSelection AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? ESCAPE '\\'" to
                (nameArgs + escapedPrefix)
        } else {
            @Suppress("DEPRECATION")
            val root = Environment.getExternalStorageDirectory()
                .absolutePath
                .replace('\\', '/')
                .trimEnd('/')
            @Suppress("DEPRECATION")
            "$musicSelection AND ${MediaStore.Audio.Media.DATA} LIKE ? ESCAPE '\\'" to
                (nameArgs + "${escapeSqlLikeArgument("$root/$relativePathPrefix")}%")
        }
    }

    // 注意:异常必须上抛(与全量查询一致),吞掉会让该卷被当成空卷而触发误删
    private fun queryChangeProbe(
        collection: MediaStoreCollection,
        selection: String,
        selectionArgs: Array<String>?,
    ): Cursor? {
        val projection = if (includeChangeProbePathColumns) {
            changeProbeProjection()
        } else {
            ChangeProbeProjection
        }
        return try {
            contentResolver.query(
                collection.uri,
                projection,
                selection,
                selectionArgs,
                null,
            )
        } catch (error: IllegalArgumentException) {
            if (!includeChangeProbePathColumns) throw error
            Log.w(TAG, "MediaStore change probe path columns unavailable; retrying without them.", error)
            includeChangeProbePathColumns = false
            contentResolver.query(
                collection.uri,
                ChangeProbeProjection,
                selection,
                selectionArgs,
                null,
            )
        }
    }

    private fun changeProbeProjection(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ChangeProbeProjection +
                MediaStore.Audio.Media.RELATIVE_PATH +
                MediaStore.MediaColumns.VOLUME_NAME
        } else {
            @Suppress("DEPRECATION")
            ChangeProbeProjection + MediaStore.Audio.Media.DATA
        }

    private fun Cursor.probeRelativePath(
        collectionVolumeName: String?,
        relativePathIndex: Int,
        volumeNameIndex: Int,
        dataIndex: Int,
    ): String? {
        if (relativePathIndex < 0 && dataIndex < 0) return null
        val rowVolumeName = if (volumeNameIndex >= 0) getStringOrNull(volumeNameIndex) else null
        val volumeName = LibraryScanPolicy.resolvedMediaStoreVolumeName(
            collectionVolumeName = collectionVolumeName,
            rowVolumeName = rowVolumeName,
        )
        return when {
            relativePathIndex >= 0 -> LibraryScanPolicy.mediaStoreRelativePathForVolume(
                volumeName = volumeName,
                mediaStoreRelativePath = getStringOrNull(relativePathIndex),
            )
            else -> {
                @Suppress("DEPRECATION")
                val storageRoot = Environment.getExternalStorageDirectory()
                    .absolutePath
                    .replace('\\', '/')
                    .trimEnd('/')
                LibraryScanPolicy.legacyDataRelativePath(
                    dataPath = getStringOrNull(dataIndex),
                    primaryStorageRoot = storageRoot,
                )
            }
        }
    }

    private fun queryAudioListing(
        collection: MediaStoreCollection,
        selection: String,
        selectionArgs: Array<String>?,
    ): Cursor? {
        return try {
            contentResolver.query(
                collection.uri,
                projection(),
                selection,
                selectionArgs,
                null,
            )
        } catch (error: IllegalArgumentException) {
            if (
                !includeSampleRateColumn ||
                !LibraryScanPolicy.isUnsupportedMediaStoreSampleRateColumn(error)
            ) {
                throw error
            }
            Log.w(TAG, "MediaStore sample_rate column is unavailable; retrying without it.", error)
            includeSampleRateColumn = false
            contentResolver.query(
                collection.uri,
                projection(),
                selection,
                selectionArgs,
                null,
            )
        }
    }

    private fun projection(): Array<String> {
        var columns = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            QProjection
        } else {
            LegacyProjection
        }
        if (LibraryScanPolicy.mediaStoreAlbumArtistColumnAvailable(Build.VERSION.SDK_INT)) {
            columns = columns + MediaStore.Audio.Media.ALBUM_ARTIST
        }
        if (includeSampleRateColumn) {
            columns = columns + SampleRateColumn
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            columns = columns + MediaStore.Audio.Media.GENRE
        }
        columns = columns + MediaStore.Audio.Media.COMPOSER
        return columns
    }

    private companion object {
        const val DefaultBatchSize = 500

        // SQLite 绑定变量上限 999,留余量
        const val IdChunkSize = 500
        const val TAG = "MediaStoreTrackScanner"

        val ChangeProbeProjection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )

        val BaseProjection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
        )

        val QProjection = BaseProjection +
            MediaStore.Audio.Media.RELATIVE_PATH +
            MediaStore.MediaColumns.VOLUME_NAME

        const val SampleRateColumn = "sample_rate"

        @Suppress("DEPRECATION")
        val LegacyProjection = BaseProjection + MediaStore.Audio.Media.DATA

    }
}

private data class MediaStoreCollection(
    val uri: Uri,
    val volumeName: String?,
)

private fun MediaStoreAudioRow.libraryId(): String = "$idPrefix$mediaId"

private fun fallbackFilesProjection(): Array<String> {
    val columns = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.DATE_MODIFIED,
    )
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        columns + MediaStore.Files.FileColumns.RELATIVE_PATH
    } else {
        columns
    }
}

private fun fallbackFilesUri(volumeName: String?): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Files.getContentUri(volumeName ?: MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Files.getContentUri("external")
    }

private fun fallbackFilesSelection(relativePathPrefix: String?): Pair<String, Array<String>> {
    val (nameSql, nameArgs) = LocalAudioFileTypes.mediaStoreFallbackNameClause(
        MediaStore.Files.FileColumns.DISPLAY_NAME,
    )
    val base =
        "$nameSql AND ${MediaStore.Files.FileColumns.MEDIA_TYPE} != ${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}" +
            " AND (${MediaStore.Files.FileColumns.MIME_TYPE} IS NULL OR ${MediaStore.Files.FileColumns.MIME_TYPE} NOT LIKE 'video/%')"
    val normalized = normalizeRelativePathPrefix(relativePathPrefix)
    if (normalized == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return base to nameArgs
    }
    return "$base AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ? ESCAPE '\\'" to
        (nameArgs + "${escapeSqlLikeArgument(normalized)}%")
}

private data class MediaStoreAudioRow(
    val mediaId: Long,
    val fileName: String?,
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String?,
    val albumArtist: String?,
    val albumId: Long?,
    val durationMs: Long,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val mimeType: String?,
    val sizeBytes: Long,
    val sampleRateHz: Int?,
    val dateModifiedSeconds: Long,
    val relativePath: String?,
    val genre: String? = null,
    val composer: String? = null,
    val idPrefix: String = LibraryScanPolicy.MediaStoreNativeIdPrefix,
)

internal fun LibraryTrackEntity.withFastPathSampleRate(
    existingTrack: TrackFingerprint?,
    readSampleRate: Boolean = true,
    sampleRateReader: (String) -> Int?,
): LibraryTrackEntity {
    if (!LibraryScanPolicy.shouldReadSampleRateFromFile(readSampleRate, sampleRateHz)) {
        return this
    }
    val fingerprintMatches = existingTrack != null && existingTrack.fingerprint == fingerprint
    val readRate = sampleRateReader(contentUri) ?: sampleRateHz
    if (readRate == sampleRateHz && fingerprintMatches) return this
    return copy(sampleRateHz = readRate).withFingerprint()
}
