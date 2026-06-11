package app.echo.android.data

import android.content.ContentResolver
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class MediaStoreTrackScanner(
    private val contentResolver: ContentResolver,
) {
    suspend fun scanAudio(
        batchSize: Int = DefaultBatchSize,
        relativePathPrefix: String? = null,
        existingTracks: Map<String, TrackFingerprint> = emptyMap(),
        onTotalCount: suspend (Int?) -> Unit = {},
        onBatch: suspend (List<LibraryTrackEntity>) -> Unit,
        onProgress: suspend (scannedCount: Int, currentTrack: LibraryTrackEntity?) -> Unit,
    ): Int {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val normalizedRelativePath = normalizeRelativePathPrefix(relativePathPrefix)
        val (selection, selectionArgs) = audioSelection(normalizedRelativePath)
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        return contentResolver.query(collection, projection(), selection, selectionArgs, sortOrder)
            ?.use { cursor ->
                cursor.scanTrackBatches(
                    collection = collection,
                    batchSize = batchSize,
                    existingTracks = existingTracks,
                    onTotalCount = onTotalCount,
                    onBatch = onBatch,
                    onProgress = onProgress,
                )
            }
            ?: 0
    }

    private suspend fun Cursor.scanTrackBatches(
        collection: Uri,
        batchSize: Int,
        existingTracks: Map<String, TrackFingerprint>,
        onTotalCount: suspend (Int?) -> Unit,
        onBatch: suspend (List<LibraryTrackEntity>) -> Unit,
        onProgress: suspend (scannedCount: Int, currentTrack: LibraryTrackEntity?) -> Unit,
    ): Int {
        val columns = MediaStoreColumns.from(this)
        val safeBatchSize = batchSize.coerceAtLeast(1)
        val batch = ArrayList<LibraryTrackEntity>(safeBatchSize)
        var scannedCount = 0
        onTotalCount(count.takeIf { it >= 0 })

        while (moveToNext()) {
            coroutineContext.ensureActive()
            runCatching {
                toTrackEntity(collection, columns, existingTracks)
            }.onSuccess { track ->
                batch += track
                scannedCount += 1
                onProgress(scannedCount, track)
                if (batch.size >= safeBatchSize) {
                    onBatch(batch.toList())
                    batch.clear()
                }
            }.onFailure { error ->
                Log.w(TAG, "Skipping unreadable MediaStore audio row.", error)
            }
        }

        if (batch.isNotEmpty()) {
            onBatch(batch.toList())
            batch.clear()
        }
        onProgress(scannedCount, null)
        return scannedCount
    }

    private fun Cursor.toTrackEntity(
        collection: Uri,
        columns: MediaStoreColumns,
        existingTracks: Map<String, TrackFingerprint>,
    ): LibraryTrackEntity {
        val mediaId = getLong(columns.idIndex)
        val trackId = "mediastore:$mediaId"
        val existingTrack = existingTracks[trackId]
        val contentUri = Uri.withAppendedPath(collection, mediaId.toString()).toString()
        val title = getStringOrNull(columns.titleIndex)?.takeIf { it.isNotBlank() } ?: "未知曲目"
        val artist = getStringOrNull(columns.artistIndex)?.takeIf { it.isNotBlank() } ?: "未知艺术家"
        val rawTrack = getLongOrNull(columns.trackIndex)?.toInt()
        val albumId = getLongOrNull(columns.albumIdIndex)?.takeIf { it > 0L }

        return LibraryTrackEntity(
            id = trackId,
            contentUri = contentUri,
            title = title,
            artist = artist,
            album = getStringOrNull(columns.albumIndex)?.takeIf { it.isNotBlank() },
            albumArtist = getStringOrNull(columns.albumArtistIndex)?.takeIf { it.isNotBlank() },
            artworkUri = albumId?.let { "content://media/external/audio/albumart/$it" },
            durationMs = getLongOrNull(columns.durationIndex) ?: 0L,
            trackNumber = rawTrack?.rem(1000)?.takeIf { it > 0 },
            discNumber = rawTrack?.div(1000)?.takeIf { it > 0 },
            year = getLongOrNull(columns.yearIndex)?.toInt()?.takeIf { it > 0 },
            mimeType = getStringOrNull(columns.mimeIndex),
            sizeBytes = getLongOrNull(columns.sizeIndex) ?: 0L,
            sampleRateHz = existingTrack?.sampleRateHz,
            dateModifiedSeconds = getLongOrNull(columns.modifiedIndex) ?: 0L,
            relativePath = relativePath(columns),
        ).withScanMetadata()
            .withFastPathSampleRate(existingTrack, ::sampleRateHz)
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

    private fun Cursor.relativePath(columns: MediaStoreColumns): String? =
        when {
            columns.relativePathIndex != null -> getStringOrNull(columns.relativePathIndex)
                ?.let(::normalizeRelativePathPrefix)
            columns.dataIndex != null -> legacyRelativePath(getStringOrNull(columns.dataIndex))
            else -> null
        }

    private fun legacyRelativePath(dataPath: String?): String? {
        val path = dataPath
            ?.replace('\\', '/')
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
        if (parent.isBlank()) return null

        @Suppress("DEPRECATION")
        val storageRoot = Environment.getExternalStorageDirectory()
            .absolutePath
            .replace('\\', '/')
            .trimEnd('/')
        return parent
            .removePrefix(storageRoot)
            .trim('/')
            .let(::normalizeRelativePathPrefix)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            QProjection
        } else {
            LegacyProjection
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

        val QProjection = BaseProjection + MediaStore.Audio.Media.RELATIVE_PATH

        @Suppress("DEPRECATION")
        val LegacyProjection = BaseProjection + MediaStore.Audio.Media.DATA
    }
}

internal fun LibraryTrackEntity.withFastPathSampleRate(
    existingTrack: TrackFingerprint?,
    sampleRateReader: (String) -> Int?,
): LibraryTrackEntity {
    if (existingTrack != null && existingTrack.fingerprint == fingerprint) return this
    return copy(sampleRateHz = sampleRateReader(contentUri))
        .withScanMetadata(lastSeenScanRunId)
}
