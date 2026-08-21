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

    suspend fun scanAudio(
        batchSize: Int = DefaultBatchSize,
        relativePathPrefix: String? = null,
        existingTracks: Map<String, TrackFingerprint> = emptyMap(),
        readSampleRate: Boolean = true,
        onTotalCount: suspend (Int?) -> Unit = {},
        onBatch: suspend (List<LibraryTrackEntity>) -> Unit,
        onProgress: suspend (scannedCount: Int, currentTrack: LibraryTrackEntity?) -> Unit,
    ): MediaStoreScanOutcome {
        val normalizedRelativePath = normalizeRelativePathPrefix(relativePathPrefix)
        val collections = audioCollections(normalizedRelativePath)
        if (collections.isEmpty()) {
            return MediaStoreScanOutcome(scannedCount = 0, querySucceeded = false)
        }
        val (selection, selectionArgs) = audioSelection(normalizedRelativePath)
        val rows = ArrayList<MediaStoreAudioRow>()
        var estimatedTotal = 0
        var querySucceeded = false
        for (collection in collections) {
            coroutineContext.ensureActive()
            val cursor = contentResolver.query(
                collection.uri,
                projection(),
                selection,
                selectionArgs,
                null,
            ) ?: continue
            querySucceeded = true
            cursor.use { listing ->
                estimatedTotal += listing.count.coerceAtLeast(0)
                onTotalCount(estimatedTotal.takeIf { it > 0 })
                rows += listing.readAudioRows(collection)
            }
        }
        if (!querySucceeded) {
            return MediaStoreScanOutcome(scannedCount = 0, querySucceeded = false)
        }
        val scannedCount = rows.scanTrackBatches(
            batchSize = batchSize,
            existingTracks = existingTracks,
            readSampleRate = readSampleRate,
            onTotalCount = onTotalCount,
            onBatch = onBatch,
            onProgress = onProgress,
        )
        return MediaStoreScanOutcome(scannedCount = scannedCount, querySucceeded = true)
    }

    private suspend fun Cursor.readAudioRows(collection: MediaStoreCollection): List<MediaStoreAudioRow> {
        val columns = MediaStoreColumns.from(this)
        val estimated = count
        val rows = ArrayList<MediaStoreAudioRow>(if (estimated > 0) estimated else 256)
        while (moveToNext()) {
            coroutineContext.ensureActive()
            runCatching { toAudioRow(collection, columns) }
                .onSuccess(rows::add)
                .onFailure { error ->
                    Log.w(TAG, "Skipping unreadable MediaStore audio row.", error)
                }
        }
        return rows
    }

    private suspend fun List<MediaStoreAudioRow>.scanTrackBatches(
        batchSize: Int,
        existingTracks: Map<String, TrackFingerprint>,
        readSampleRate: Boolean,
        onTotalCount: suspend (Int?) -> Unit,
        onBatch: suspend (List<LibraryTrackEntity>) -> Unit,
        onProgress: suspend (scannedCount: Int, currentTrack: LibraryTrackEntity?) -> Unit,
    ): Int {
        val safeBatchSize = batchSize.coerceAtLeast(1)
        val batch = ArrayList<LibraryTrackEntity>(safeBatchSize)
        var scannedCount = 0
        onTotalCount(size)

        for (row in this) {
            coroutineContext.ensureActive()
            val track = row.toTrackEntity(existingTracks, readSampleRate)
            batch += track
            scannedCount += 1
            onProgress(scannedCount, track)
            if (batch.size >= safeBatchSize) {
                onBatch(batch.toList())
                batch.clear()
            }
        }

        if (batch.isNotEmpty()) {
            onBatch(batch.toList())
            batch.clear()
        }
        onProgress(scannedCount, null)
        return scannedCount
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
            albumArtist = getStringOrNull(columns.albumArtistIndex)?.takeIf { it.isNotBlank() },
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
        return entity.withFastPathSampleRate(existingTrack, readSampleRate, ::sampleRateHz)
    }

    private fun sampleRateHz(contentUri: String): Int? =
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
        val albumArtistIndex: Int,
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
                    albumArtistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST),
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

    private fun projection(): Array<String> =
        when {
            LibraryScanPolicy.mediaStoreSampleRateColumnAvailable(Build.VERSION.SDK_INT) -> SProjection
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> QProjection
            else -> LegacyProjection
        }

    private companion object {
        const val DefaultBatchSize = 500
        const val TAG = "MediaStoreTrackScanner"

        val BaseProjection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ARTIST,
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

        val SProjection = QProjection + SampleRateColumn

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
