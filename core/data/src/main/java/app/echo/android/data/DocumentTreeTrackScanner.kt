package app.echo.android.data

import android.content.ContentResolver
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import kotlinx.coroutines.ensureActive
import java.util.ArrayDeque
import java.util.Locale
import kotlin.coroutines.coroutineContext

class DocumentTreeTrackScanner(
    private val contentResolver: ContentResolver,
) {
    suspend fun scanAudioTree(
        treeUri: Uri,
        relativePathPrefix: String,
        batchSize: Int = DefaultBatchSize,
        existingTracks: Map<String, TrackFingerprint> = emptyMap(),
        readSampleRate: Boolean = true,
        onBatch: suspend (List<LibraryTrackEntity>) -> Unit,
        onProgress: suspend (scannedCount: Int, currentTrack: LibraryTrackEntity?) -> Unit,
    ): MediaStoreScanOutcome {
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val safeBatchSize = batchSize.coerceAtLeast(1)
        val batch = ArrayList<LibraryTrackEntity>(safeBatchSize)
        val pendingDirectories = ArrayDeque<DocumentTreeDirectory>()
        var scannedCount = 0
        var querySucceeded = true

        pendingDirectories.add(DocumentTreeDirectory(rootDocumentId, relativePath = ""))
        while (!pendingDirectories.isEmpty()) {
            coroutineContext.ensureActive()
            val directory = pendingDirectories.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directory.documentId)
            val cursor = try {
                contentResolver.query(childrenUri, Projection, null, null, null)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Document tree directory listing failed.", error)
                querySucceeded = false
                continue
            }
            if (cursor == null) {
                querySucceeded = false
                continue
            }
            cursor.use { listing ->
                val columns = DocumentColumns.from(listing)
                while (listing.moveToNext()) {
                    coroutineContext.ensureActive()
                    val documentId = listing.getStringOrNull(columns.documentIdIndex) ?: continue
                    val name = listing.getStringOrNull(columns.nameIndex)
                        ?.takeIf { it.isNotBlank() }
                        ?: documentId.substringAfterLast('/')
                    val mimeType = listing.getStringOrNull(columns.mimeTypeIndex)

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pendingDirectories.add(
                            DocumentTreeDirectory(
                                documentId = documentId,
                                relativePath = appendRelativePath(directory.relativePath, name),
                            ),
                        )
                        continue
                    }

                    if (!isSupportedAudio(name, mimeType)) continue
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    val sizeBytes = listing.getOptionalLong(columns.sizeIndex) ?: 0L
                    val lastModifiedMs = listing.getOptionalLong(columns.lastModifiedIndex) ?: 0L
                    runCatching {
                        documentUri.toTrackEntity(
                            documentId = documentId,
                            displayName = name,
                            mimeType = resolvedAudioMimeType(name, mimeType),
                            sizeBytes = sizeBytes,
                            lastModifiedMs = lastModifiedMs,
                            relativePath = combineRelativePath(relativePathPrefix, directory.relativePath),
                            existingTrack = existingTracks["saf:${Uri.encode(documentId)}"],
                            readSampleRate = readSampleRate,
                        )
                    }.onSuccess { track ->
                        batch += track
                        scannedCount += 1
                        onProgress(scannedCount, track)
                        if (batch.size >= safeBatchSize) {
                            onBatch(batch.toList())
                            batch.clear()
                        }
                    }.onFailure { error ->
                        Log.w(TAG, "Skipping unreadable document tree audio file.", error)
                    }
                }
            }
        }

        if (batch.isNotEmpty()) {
            onBatch(batch.toList())
            batch.clear()
        }
        onProgress(scannedCount, null)
        return MediaStoreScanOutcome(scannedCount = scannedCount, querySucceeded = querySucceeded)
    }

    private fun Uri.toTrackEntity(
        documentId: String,
        displayName: String,
        mimeType: String?,
        sizeBytes: Long,
        lastModifiedMs: Long,
        relativePath: String,
        existingTrack: TrackFingerprint?,
        readSampleRate: Boolean,
    ): LibraryTrackEntity {
        val dateModifiedSeconds = lastModifiedMs.toEpochSeconds()
        if (
            existingTrack != null &&
            LibraryScanPolicy.shouldReuseUnchangedDocumentFingerprint(
                existingContentUri = existingTrack.contentUri,
                incomingContentUri = toString(),
                existingSizeBytes = existingTrack.sizeBytes,
                incomingSizeBytes = sizeBytes,
                existingDateModifiedSeconds = existingTrack.dateModifiedSeconds,
                incomingDateModifiedSeconds = dateModifiedSeconds,
            )
        ) {
            return LibraryTrackEntity(
                id = "saf:${Uri.encode(documentId)}",
                contentUri = toString(),
                title = displayName.removeAudioExtension(),
                artist = "Unknown artist",
                album = null,
                albumArtist = null,
                artworkUri = null,
                durationMs = 0L,
                trackNumber = null,
                discNumber = null,
                year = null,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                sampleRateHz = existingTrack.sampleRateHz,
                dateModifiedSeconds = dateModifiedSeconds,
                relativePath = relativePath,
                fingerprint = existingTrack.fingerprint,
                source = LibraryScanPolicy.SafSourceId,
            )
        }
        val metadata = readMetadata(this, readSampleRate)
        val title = metadata.title?.takeIf { it.isNotBlank() } ?: displayName.removeAudioExtension()
        val artist = metadata.artist?.takeIf { it.isNotBlank() } ?: "Unknown artist"
        return LibraryTrackEntity(
            id = "saf:${Uri.encode(documentId)}",
            contentUri = toString(),
            title = title,
            artist = artist,
            album = metadata.album?.takeIf { it.isNotBlank() },
            albumArtist = metadata.albumArtist?.takeIf { it.isNotBlank() },
            artworkUri = null,
            durationMs = metadata.durationMs,
            trackNumber = metadata.trackNumber,
            discNumber = metadata.discNumber,
            year = metadata.year,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            sampleRateHz = metadata.sampleRateHz,
            dateModifiedSeconds = lastModifiedMs.toEpochSeconds(),
            relativePath = relativePath,
            source = LibraryScanPolicy.SafSourceId,
        ).withScanMetadata()
    }

    private fun readMetadata(uri: Uri, readSampleRate: Boolean): DocumentAudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                retriever.setDataSource(descriptor.fileDescriptor)
                DocumentAudioMetadata(
                    title = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    artist = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    album = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    albumArtist = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                    durationMs = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                        ?.coerceAtLeast(0L)
                        ?: 0L,
                    trackNumber = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                        .parseLeadingPositiveInt(),
                    discNumber = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                        .parseLeadingPositiveInt(),
                    year = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                        ?.take(4)
                        ?.toIntOrNull()
                        ?.takeIf { it > 0 },
                    sampleRateHz = if (readSampleRate) {
                        retriever.metadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                            ?.toIntOrNull()
                            ?.takeIf { it > 0 }
                    } else {
                        null
                    },
                )
            } ?: DocumentAudioMetadata()
        } catch (error: Throwable) {
            Log.d(TAG, "Unable to read document tree audio metadata for $uri.", error)
            DocumentAudioMetadata()
        } finally {
            retriever.release()
        }
    }

    private data class DocumentTreeDirectory(
        val documentId: String,
        val relativePath: String,
    )

    private data class DocumentColumns(
        val documentIdIndex: Int,
        val nameIndex: Int,
        val mimeTypeIndex: Int,
        val sizeIndex: Int,
        val lastModifiedIndex: Int,
    ) {
        companion object {
            fun from(cursor: Cursor): DocumentColumns =
                DocumentColumns(
                    documentIdIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                    nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    mimeTypeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE),
                    sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE),
                    lastModifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                )
        }
    }

    private data class DocumentAudioMetadata(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val albumArtist: String? = null,
        val durationMs: Long = 0L,
        val trackNumber: Int? = null,
        val discNumber: Int? = null,
        val year: Int? = null,
        val sampleRateHz: Int? = null,
    )

    private companion object {
        const val DefaultBatchSize = 200
        const val TAG = "DocumentTreeScanner"

        val Projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}

private fun MediaMetadataRetriever.metadata(keyCode: Int): String? =
    extractMetadata(keyCode)?.takeIf { it.isNotBlank() }

private fun Cursor.getOptionalLong(columnIndex: Int): Long? =
    if (columnIndex >= 0) getLongOrNull(columnIndex) else null

private fun String?.parseLeadingPositiveInt(): Int? =
    this
        ?.trim()
        ?.takeWhile { it.isDigit() }
        ?.toIntOrNull()
        ?.takeIf { it > 0 }

private fun Long.toEpochSeconds(): Long =
    when {
        this <= 0L -> 0L
        this > 9_999_999_999L -> this / 1000L
        else -> this
    }

private fun appendRelativePath(parent: String, child: String): String =
    listOf(parent.trim('/'), child.trim('/'))
        .filter { it.isNotBlank() }
        .joinToString("/")

private fun combineRelativePath(prefix: String, folderPath: String): String =
    normalizeRelativePathPrefix(
        listOf(prefix.trim('/'), folderPath.trim('/'))
            .filter { it.isNotBlank() }
            .joinToString("/"),
    ) ?: prefix

private fun isSupportedAudio(name: String, mimeType: String?): Boolean =
    mimeType?.startsWith("audio/", ignoreCase = true) == true || name.audioMimeType() != null

private fun resolvedAudioMimeType(name: String, mimeType: String?): String? =
    mimeType?.takeIf { it.startsWith("audio/", ignoreCase = true) } ?: name.audioMimeType()

private fun String.audioMimeType(): String? {
    val name = lowercase(Locale.ROOT)
    return when {
        name.endsWith(".flac") -> "audio/flac"
        name.endsWith(".mp3") -> "audio/mpeg"
        name.endsWith(".m4a") || name.endsWith(".mp4") -> "audio/mp4"
        name.endsWith(".aac") -> "audio/aac"
        name.endsWith(".ogg") || name.endsWith(".oga") -> "audio/ogg"
        name.endsWith(".opus") -> "audio/opus"
        name.endsWith(".wav") -> "audio/wav"
        name.endsWith(".aiff") || name.endsWith(".aif") -> "audio/aiff"
        name.endsWith(".ape") -> "audio/ape"
        name.endsWith(".dsf") -> "audio/dsf"
        name.endsWith(".dff") -> "audio/dff"
        else -> null
    }
}

private fun String.removeAudioExtension(): String =
    substringBeforeLast('.', missingDelimiterValue = this)
