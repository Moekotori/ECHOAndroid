package app.echo.android.data

import app.echo.android.model.library.LibraryScanOptions
import kotlinx.coroutines.CancellationException
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
import kotlin.coroutines.coroutineContext

class DocumentTreeTrackScanner(
    private val contentResolver: ContentResolver,
) {
    suspend fun scanAudioTree(
        treeUri: Uri,
        relativePathPrefix: String,
        batchSize: Int = DefaultBatchSize,
        existingTracks: Map<String, TrackFingerprint> = emptyMap(),
        mediaStoreDuplicateKeys: Map<String, LibraryTrackEntity> = emptyMap(),
        readSampleRate: Boolean = true,
        options: LibraryScanOptions = LibraryScanOptions(0L, 0L, false, false),
        onDuplicate: suspend (oldId: String, targetId: String) -> Unit = { _, _ -> },
        rejectedFiles: LocalScanFilterCache? = null,
        onSkipped: suspend () -> Unit = {},
        onBatch: suspend (List<LibraryTrackEntity>) -> Unit,
        onProgress: suspend (scannedCount: Int, currentTrack: LibraryTrackEntity?) -> Unit,
    ): MediaStoreScanOutcome {
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val safeBatchSize = batchSize.coerceAtLeast(1)
        val batch = ArrayList<LibraryTrackEntity>(safeBatchSize)
        val pendingDirectories = ArrayDeque<DocumentTreeDirectory>()
        var scannedCount = 0
        var querySucceeded = true
        var failedReads = 0
        var excludedDirectories = 0

        pendingDirectories.add(DocumentTreeDirectory(rootDocumentId, relativePath = ""))
        while (!pendingDirectories.isEmpty()) {
            coroutineContext.ensureActive()
            val directory = pendingDirectories.removeFirst()
            if (!options.includesDirectory(combineRelativePath(relativePathPrefix, directory.relativePath))) {
                excludedDirectories++
                continue
            }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directory.documentId)
            val cursor = try {
                contentResolver.query(childrenUri, Projection, null, null, null)
            } catch (error: CancellationException) {
                throw error
            } catch (error: RuntimeException) {
                Log.w(TAG, "Document tree directory listing failed.", error)
                querySucceeded = false
                failedReads++
                continue
            }
            if (cursor == null) {
                querySucceeded = false
                failedReads++
                continue
            }
            val audioRows = ArrayList<DocumentAudioRow>()
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
                    audioRows += DocumentAudioRow(
                        documentId = documentId,
                        documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                        displayName = name,
                        mimeType = resolvedAudioMimeType(name, mimeType),
                        sizeBytes = listing.getOptionalLong(columns.sizeIndex) ?: 0L,
                        lastModifiedMs = listing.getOptionalLong(columns.lastModifiedIndex) ?: 0L,
                        relativePath = combineRelativePath(relativePathPrefix, directory.relativePath),
                    )
                }
            }
            for (row in audioRows) {
                coroutineContext.ensureActive()
                val duplicateKey = LibraryScanPolicy.localFileDuplicateKey(
                    relativePath = row.relativePath,
                    sizeBytes = row.sizeBytes,
                    dateModifiedSeconds = row.lastModifiedMs.toEpochSeconds(),
                    displayName = row.displayName,
                )
                val duplicate = duplicateKey?.let(mediaStoreDuplicateKeys::get)
                if (duplicate != null) {
                    onDuplicate("saf:${Uri.encode(row.documentId)}", duplicate.id)
                    // Keep the stable MediaStore ID, but refresh changed tags and folder summaries too.
                    batch += duplicate
                    scannedCount += 1
                    onProgress(scannedCount, duplicate)
                    if (batch.size >= safeBatchSize) {
                        onBatch(batch.toList())
                        batch.clear()
                    }
                    continue
                }
                val trackId = "saf:${Uri.encode(row.documentId)}"
                if (trackId !in existingTracks && (
                        (row.sizeBytes > 0L && row.sizeBytes < options.minSizeBytes) ||
                            rejectedFiles?.shouldSkip(row.documentUri.toString(), row.sizeBytes, row.lastModifiedMs.toEpochSeconds(), options) == true
                    )) {
                    scannedCount++
                    onSkipped()
                    onProgress(scannedCount, null)
                    continue
                }
                runCatching {
                    row.documentUri.toTrackEntity(
                        documentId = row.documentId,
                        displayName = row.displayName,
                        mimeType = row.mimeType,
                        sizeBytes = row.sizeBytes,
                        lastModifiedMs = row.lastModifiedMs,
                        relativePath = row.relativePath,
                        existingTrack = existingTracks["saf:${Uri.encode(row.documentId)}"],
                        readSampleRate = readSampleRate,
                    )
                }.onSuccess { track ->
                    if (track.fingerprint == LibraryScanPolicy.PendingDocumentMetadataFingerprint) {
                        querySucceeded = false
                        failedReads++
                    }
                    if (track.id !in existingTracks &&
                        track.fingerprint != LibraryScanPolicy.PendingDocumentMetadataFingerprint &&
                        !options.accepts(track.durationMs, track.sizeBytes, null)) {
                        rejectedFiles?.remember(track.contentUri, track.sizeBytes, track.dateModifiedSeconds, track.durationMs)
                        scannedCount++
                        onSkipped()
                        onProgress(scannedCount, null)
                        return@onSuccess
                    }
                    batch += track
                    scannedCount += 1
                    onProgress(scannedCount, track)
                    if (batch.size >= safeBatchSize) {
                        onBatch(batch.toList())
                        batch.clear()
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    querySucceeded = false
                    failedReads++
                    Log.w(TAG, "Skipping unreadable document tree audio file.", error)
                }
            }
        }

        if (batch.isNotEmpty()) {
            onBatch(batch.toList())
            batch.clear()
        }
        onProgress(scannedCount, null)
        return MediaStoreScanOutcome(scannedCount = scannedCount, querySucceeded = querySucceeded,
            failedReadCount = failedReads, excludedDirectoryCount = excludedDirectories)
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
            existingTrack.durationMs > 0L &&
            existingTrack.fingerprint != null &&
            existingTrack.fingerprint != LibraryScanPolicy.PendingDocumentMetadataFingerprint &&
            LibraryScanPolicy.shouldReuseUnchangedDocumentFingerprint(
                existingContentUri = existingTrack.contentUri,
                incomingContentUri = toString(),
                existingSizeBytes = existingTrack.sizeBytes,
                incomingSizeBytes = sizeBytes,
                existingDateModifiedSeconds = existingTrack.dateModifiedSeconds,
                incomingDateModifiedSeconds = dateModifiedSeconds,
                existingRelativePath = existingTrack.relativePath,
                incomingRelativePath = relativePath,
            )
        ) {
            return LibraryTrackEntity(
                id = "saf:${Uri.encode(documentId)}",
                contentUri = toString(),
                title = displayName.removeAudioExtension(),
                artist = canonicalUnknownArtist(),
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
        val metadata = readMetadata(this, readSampleRate, mimeType, displayName)
        val title = metadata.title.takeUnlessUnknownMetadata() ?: displayName.removeAudioExtension()
        val artist = metadata.artist.takeUnlessUnknownMetadata() ?: canonicalUnknownArtist()
        return LibraryTrackEntity(
            id = "saf:${Uri.encode(documentId)}",
            contentUri = toString(),
            title = title,
            artist = artist,
            album = metadata.album.takeUnlessUnknownMetadata(),
            albumArtist = metadata.albumArtist.takeUnlessUnknownMetadata(),
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
        ).withFingerprint().let { track ->
            if (metadata.readSucceeded) track else track.copy(fingerprint = LibraryScanPolicy.PendingDocumentMetadataFingerprint)
        }
    }

    private fun readMetadata(
        uri: Uri,
        readSampleRate: Boolean,
        mimeType: String?,
        displayName: String,
    ): DocumentAudioMetadata {
        val fileTags = if (LibraryWavTagPolicy.isWavContainer(mimeType, displayName)) {
            runCatching { contentResolver.openInputStream(uri)?.use(::readLocalAudioTags) }.getOrNull()
        } else {
            null
        }
        val retriever = MediaMetadataRetriever()
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                retriever.setDataSource(descriptor.fileDescriptor)
                DocumentAudioMetadata(
                    title = fileTags?.title?.takeIf { it.isNotBlank() }
                        ?: retriever.metadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    artist = fileTags?.artist?.takeIf { it.isNotBlank() }
                        ?: retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    album = fileTags?.album?.takeIf { it.isNotBlank() }
                        ?: retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    albumArtist = fileTags?.albumArtist?.takeIf { it.isNotBlank() }
                        ?: retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                    durationMs = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                        ?.coerceAtLeast(0L)
                        ?: 0L,
                    trackNumber = fileTags?.trackNumber
                        ?: retriever.metadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                            .parseLeadingPositiveInt(),
                    discNumber = fileTags?.discNumber
                        ?: retriever.metadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                            .parseLeadingPositiveInt(),
                    year = fileTags?.year
                        ?: retriever.metadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
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
            } ?: fileTags.toDocumentMetadata(readSucceeded = false)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.d(TAG, "Unable to read document tree audio metadata for $uri.", error)
            fileTags.toDocumentMetadata(readSucceeded = false)
        } finally {
            retriever.release()
        }
    }

    private fun AudioTagFields?.toDocumentMetadata(readSucceeded: Boolean): DocumentAudioMetadata =
        DocumentAudioMetadata(
            readSucceeded = readSucceeded,
            title = this?.title?.takeIf { it.isNotBlank() },
            artist = this?.artist?.takeIf { it.isNotBlank() },
            album = this?.album?.takeIf { it.isNotBlank() },
            albumArtist = this?.albumArtist?.takeIf { it.isNotBlank() },
            trackNumber = this?.trackNumber,
            discNumber = this?.discNumber,
            year = this?.year,
        )

    private data class DocumentTreeDirectory(
        val documentId: String,
        val relativePath: String,
    )

    private data class DocumentAudioRow(
        val documentId: String,
        val documentUri: Uri,
        val displayName: String,
        val mimeType: String?,
        val sizeBytes: Long,
        val lastModifiedMs: Long,
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
        val readSucceeded: Boolean = true,
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
    LocalAudioFileTypes.isSupported(name, mimeType)

private fun resolvedAudioMimeType(name: String, mimeType: String?): String? =
    LocalAudioFileTypes.resolvedMimeType(name, mimeType)

private fun String.removeAudioExtension(): String =
    substringBeforeLast('.', missingDelimiterValue = this)
