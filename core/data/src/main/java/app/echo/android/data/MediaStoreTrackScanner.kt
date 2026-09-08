package app.echo.android.data

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
    private var includeSampleRateColumn =
        LibraryScanPolicy.mediaStoreSampleRateColumnAvailable(Build.VERSION.SDK_INT)

    suspend fun scanAudio(
        batchSize: Int = DefaultBatchSize,
        relativePathPrefix: String? = null,
        existingTracks: Map<String, TrackFingerprint> = emptyMap(),
        readSampleRate: Boolean = true,
        onTotalCount: suspend (Int?) -> Unit = {},
        onUnchangedIds: suspend (List<String>) -> Unit = {},
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
                val track = runCatching {
                    listing.toAudioRow(collection, columns)
                        .toTrackEntity(existingTracks, readSampleRate)
                }.onFailure { error ->
                    complete = false
                    Log.w(TAG, "Skipping unreadable MediaStore audio row.", error)
                }.getOrNull() ?: continue
                batch += track
                scannedCount += 1
                onProgress(scannedCount, track)
                if (batch.size >= safeBatchSize) {
                    flushBatch()
                }
            }
            return complete
        }

        // 只有全部查询成功的卷才算"完整扫过":null 游标(卷 provider 短暂不可用等)
        // 会让该卷缺席 seenIds,若仍计入完整卷,清理阶段就会把整卷曲目误删
        val completeVolumeScopes = ArrayList<MediaStoreVolumeScope>()
        for (collection in collections) {
            coroutineContext.ensureActive()
            val volumeScope = LibraryScanPolicy.mediaStoreVolumeScope(collection.volumeName)
            if (existingTracks.isEmpty()) {
                // 首扫:直接全列拉取
                val cursor = queryAudioListing(collection, selection, selectionArgs) ?: continue
                querySucceeded = true
                val complete = cursor.use { listing ->
                    estimatedTotal += listing.count.coerceAtLeast(0)
                    onTotalCount(estimatedTotal.takeIf { it > 0 })
                    consumeFullListing(listing, collection)
                }
                if (complete) completeVolumeScopes += volumeScope
                continue
            }
            // 增量:先用 _ID/DATE_MODIFIED/SIZE 三列轻量游标与库内快照比对,
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
                val unchangedIds = ArrayList<String>()
                while (listing.moveToNext()) {
                    coroutineContext.ensureActive()
                    val mediaId = listing.getLong(idIndex)
                    val trackId = "${LibraryScanPolicy.MediaStoreNativeIdPrefix}$mediaId"
                    val unchanged = LibraryScanPolicy.isMediaStoreRowUnchanged(
                        existing = existingTracks[trackId],
                        dateModifiedSeconds = listing.getLongOrNull(modifiedIndex) ?: 0L,
                        sizeBytes = listing.getLongOrNull(sizeIndex) ?: 0L,
                    )
                    if (unchanged) {
                        unchangedIds += trackId
                        scannedCount += 1
                    } else {
                        changedMediaIds += mediaId
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
        }
        if (!querySucceeded) {
            return MediaStoreScanOutcome(scannedCount = 0, querySucceeded = false)
        }
        flushBatch()
        onProgress(scannedCount, null)
        return MediaStoreScanOutcome(
            scannedCount = scannedCount,
            querySucceeded = true,
            completeVolumeScopes = completeVolumeScopes,
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
                        collection.uri, projection().filterNot { it == SampleRateColumn }.toTypedArray() + MediaStore.Audio.Media.DISPLAY_NAME,
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
            title = getStringOrNull(columns.titleIndex)?.takeIf { it.isNotBlank() } ?: "未知曲目",
            artist = getStringOrNull(columns.artistIndex)?.takeIf { it.isNotBlank() } ?: "未知艺术家",
            album = getStringOrNull(columns.albumIndex)?.takeIf { it.isNotBlank() },
            albumArtist = columns.albumArtistIndex
                ?.let { getStringOrNull(it) }
                ?.takeIf { it.isNotBlank() },
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
        )
    }

    private fun MediaStoreAudioRow.toTrackEntity(
        existingTracks: Map<String, TrackFingerprint>,
        readSampleRate: Boolean,
    ): LibraryTrackEntity {
        val trackId = "mediastore:$mediaId"
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
            sizeBytes = sizeBytes,
            sampleRateHz = LibraryScanPolicy.preferredSampleRateHz(sampleRateHz, existingTrack?.sampleRateHz),
            dateModifiedSeconds = dateModifiedSeconds,
            relativePath = relativePath,
        ).withFingerprint()
        return entity.withFastPathSampleRate(existingTrack, readSampleRate, ::readSampleRateHz)
    }

    internal fun readSampleRateHz(contentUri: String): Int? =
        runCatching {
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
    ) {
        companion object {
            fun from(cursor: Cursor): MediaStoreColumns =
                MediaStoreColumns(
                    idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID),
                    titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE),
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
                )
        }
    }

    private fun audioSelection(relativePathPrefix: String?): Pair<String, Array<String>?> {
        val musicSelection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        if (relativePathPrefix == null) return musicSelection to null

        val escapedPrefix = "${escapeSqlLikeArgument(relativePathPrefix)}%"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "$musicSelection AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? ESCAPE '\\'" to
                arrayOf(escapedPrefix)
        } else {
            @Suppress("DEPRECATION")
            val root = Environment.getExternalStorageDirectory()
                .absolutePath
                .replace('\\', '/')
                .trimEnd('/')
            @Suppress("DEPRECATION")
            "$musicSelection AND ${MediaStore.Audio.Media.DATA} LIKE ? ESCAPE '\\'" to
                arrayOf("${escapeSqlLikeArgument("$root/$relativePathPrefix")}%")
        }
    }

    // 注意:异常必须上抛(与全量查询一致),吞掉会让该卷被当成空卷而触发误删
    private fun queryChangeProbe(
        collection: MediaStoreCollection,
        selection: String,
        selectionArgs: Array<String>?,
    ): Cursor? =
        contentResolver.query(
            collection.uri,
            ChangeProbeProjection,
            selection,
            selectionArgs,
            null,
        )

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
        )

        val BaseProjection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
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

private data class MediaStoreAudioRow(
    val mediaId: Long,
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
