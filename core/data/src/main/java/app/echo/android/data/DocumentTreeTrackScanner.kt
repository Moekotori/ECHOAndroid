package app.echo.android.data

import app.echo.android.model.library.CueSheet
import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.LibraryScanOptions
import app.echo.android.model.platform.EchoPlatformCapabilities
import kotlinx.coroutines.CancellationException
import android.content.ContentResolver
import java.io.ByteArrayOutputStream
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
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
        onUnchangedIds: suspend (List<String>) -> Unit = {},
        listings: DocumentTreeListingCache? = null,
        reuseDirectoryListings: Boolean = true,
        onBatch: suspend (List<LibraryTrackEntity>) -> Unit,
        onProgress: suspend (scannedCount: Int, currentTrack: LibraryTrackEntity?) -> Unit,
    ): MediaStoreScanOutcome {
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val safeBatchSize = batchSize.coerceAtLeast(1)
        val batch = ArrayList<LibraryTrackEntity>(safeBatchSize)
        val pendingDirectories = ArrayDeque<DocumentTreeDirectory>()
        var scannedCount = 0
        var unmatchedCueCount = 0
        var querySucceeded = true
        var failedReads = 0
        var excludedDirectories = 0

        pendingDirectories.add(DocumentTreeDirectory(rootDocumentId, relativePath = "", lastModifiedMs = 0L))
        val treeKey = treeUri.toString()
        while (!pendingDirectories.isEmpty()) {
            coroutineContext.ensureActive()
            val directory = pendingDirectories.removeFirst()
            if (!options.includesDirectory(combineRelativePath(relativePathPrefix, directory.relativePath))) {
                excludedDirectories++
                continue
            }
            val isTreeRoot = directory.relativePath.isEmpty()
            val children = listings
                ?.takeIf { reuseDirectoryListings }
                ?.listing(
                    treeUri = treeKey,
                    documentId = directory.documentId,
                    lastModifiedMs = directory.lastModifiedMs,
                    isTreeRoot = isTreeRoot,
                )
                ?: queryDirectoryChildren(treeUri, directory.documentId)?.also { rows ->
                listings?.remember(
                    treeUri = treeKey,
                    documentId = directory.documentId,
                    lastModifiedMs = directory.lastModifiedMs,
                    isTreeRoot = isTreeRoot,
                    children = rows,
                )
            }
            if (children == null) {
                querySucceeded = false
                failedReads++
                continue
            }
            val audioRows = ArrayList<DocumentAudioRow>()
            val cueRows = ArrayList<DocumentAudioRow>()
            for (child in children) {
                coroutineContext.ensureActive()
                if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    val childRelativePath = appendRelativePath(directory.relativePath, child.name)
                    if (!options.includesDirectory(combineRelativePath(relativePathPrefix, childRelativePath))) {
                        excludedDirectories++
                        continue
                    }
                    pendingDirectories.add(
                        DocumentTreeDirectory(
                            documentId = child.documentId,
                            relativePath = childRelativePath,
                            lastModifiedMs = child.lastModifiedMs,
                        ),
                    )
                    continue
                }
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.documentId)
                val childRelative = combineRelativePath(relativePathPrefix, directory.relativePath)
                if (LocalAudioFileTypes.isCueSheet(child.name, child.mimeType)) {
                    cueRows += DocumentAudioRow(
                        documentId = child.documentId,
                        documentUri = childUri,
                        displayName = child.name,
                        mimeType = child.mimeType,
                        sizeBytes = child.sizeBytes,
                        lastModifiedMs = child.lastModifiedMs,
                        relativePath = childRelative,
                    )
                    continue
                }
                if (!isSupportedAudio(child.name, child.mimeType)) continue
                audioRows += DocumentAudioRow(
                    documentId = child.documentId,
                    documentUri = childUri,
                    displayName = child.name,
                    mimeType = resolvedAudioMimeType(child.name, child.mimeType),
                    sizeBytes = child.sizeBytes,
                    lastModifiedMs = child.lastModifiedMs,
                    relativePath = childRelative,
                )
            }
            val (cueByAudioName, unmatchedInDirectory) = cueSheetsByAudioName(cueRows, audioRows.map { it.displayName })
            unmatchedCueCount += unmatchedInDirectory
            val unchangedIds = ArrayList<String>()
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
                val existingCueIds = existingTracks.keys.filter { id ->
                    CueSheetPolicy.isCueTrackId(id) && CueSheetPolicy.baseTrackId(id) == trackId
                }
                val existingTrack = existingTracks[trackId] ?: existingCueIds.firstOrNull()?.let(existingTracks::get)
                val cueSheet = cueByAudioName[row.displayName]
                if (
                    cueSheet == null &&
                    existingCueIds.isEmpty() &&
                    LibraryScanPolicy.shouldReuseUnchangedDocumentTrack(
                        existing = existingTrack?.copy(
                            contentUri = CueSheetPolicy.playbackUri(existingTrack.contentUri),
                        ),
                        incomingContentUri = row.documentUri.toString(),
                        incomingSizeBytes = row.sizeBytes,
                        incomingDateModifiedSeconds = row.lastModifiedMs.toEpochSeconds(),
                        incomingRelativePath = row.relativePath,
                    )
                ) {
                    unchangedIds += trackId
                    scannedCount += 1
                    continue
                }
                if (!options.acceptsFileFormat(row.displayName, existingTrack != null)) {
                    scannedCount++
                    onSkipped()
                    onProgress(scannedCount, null)
                    continue
                }
                if (existingTrack == null && (
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
                        existingTrack = existingTrack,
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
                    val expanded = cueSheet?.let { track.splitByCue(it, row.displayName) } ?: listOf(track)
                    expanded.forEach { item ->
                        batch += item
                        scannedCount += 1
                        onProgress(scannedCount, item)
                        if (batch.size >= safeBatchSize) {
                            onBatch(batch.toList())
                            batch.clear()
                        }
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    querySucceeded = false
                    failedReads++
                    Log.w(TAG, "Skipping unreadable document tree audio file.", error)
                }
            }
            if (unchangedIds.isNotEmpty()) {
                onUnchangedIds(unchangedIds)
                onProgress(scannedCount, null)
            }
        }

        if (batch.isNotEmpty()) {
            onBatch(batch.toList())
            batch.clear()
        }
        onProgress(scannedCount, null)
        return MediaStoreScanOutcome(
            scannedCount = scannedCount,
            querySucceeded = querySucceeded,
            failedReadCount = failedReads,
            excludedDirectoryCount = excludedDirectories,
            unmatchedCueCount = unmatchedCueCount,
        )
    }

    private fun cueSheetsByAudioName(
        cueRows: List<DocumentAudioRow>,
        audioNames: List<String>,
    ): Pair<Map<String, CueSheet>, Int> {
        if (cueRows.isEmpty()) return emptyMap<String, CueSheet>() to 0
        if (audioNames.isEmpty()) return emptyMap<String, CueSheet>() to cueRows.size
        val matched = LinkedHashMap<String, CueSheet>()
        var unmatched = 0
        for (row in cueRows) {
            if (row.sizeBytes > CueSheetPolicy.MaxCueBytes) {
                unmatched++
                continue
            }
            val sheet = readCueSheet(row.documentUri)
            if (sheet == null) {
                unmatched++
                continue
            }
            val wantedNames = buildList {
                add(row.displayName)
                sheet.fileName?.let(::add)
                sheet.tracks.forEach { track -> track.fileName?.let(::add) }
            }
            val hits = wantedNames.mapNotNull { wanted -> CueSheetPolicy.matchAudioName(wanted, audioNames) }
            if (hits.isEmpty()) {
                unmatched++
            } else {
                hits.forEach { audioName -> matched.putIfAbsent(audioName, sheet) }
            }
        }
        return matched to unmatched
    }

    private fun readCueSheet(uri: Uri): CueSheet? {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > CueSheetPolicy.MaxCueBytes) return null
                    output.write(buffer, 0, count)
                }
                CueSheetParser.parse(output.toByteArray())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun queryDirectoryChildren(treeUri: Uri, documentId: String): List<DocumentTreeCachedChild>? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val cursor = try {
            contentResolver.query(childrenUri, Projection, null, null, null)
        } catch (error: CancellationException) {
            throw error
        } catch (error: RuntimeException) {
            Log.w(TAG, "Document tree directory listing failed.", error)
            return null
        } ?: return null
        return cursor.use { listing ->
            val columns = DocumentColumns.from(listing)
            val rows = ArrayList<DocumentTreeCachedChild>()
            while (listing.moveToNext()) {
                coroutineContext.ensureActive()
                val childDocumentId = listing.getStringOrNull(columns.documentIdIndex) ?: continue
                val name = listing.getStringOrNull(columns.nameIndex)
                    ?.takeIf { it.isNotBlank() }
                    ?: childDocumentId.substringAfterLast('/')
                rows += DocumentTreeCachedChild(
                    documentId = childDocumentId,
                    name = name,
                    mimeType = listing.getStringOrNull(columns.mimeTypeIndex),
                    sizeBytes = listing.getOptionalLong(columns.sizeIndex) ?: 0L,
                    lastModifiedMs = listing.getOptionalLong(columns.lastModifiedIndex) ?: 0L,
                )
            }
            rows
        }
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
            LibraryScanPolicy.shouldReuseUnchangedDocumentTrack(
                existing = existingTrack,
                incomingContentUri = toString(),
                incomingSizeBytes = sizeBytes,
                incomingDateModifiedSeconds = dateModifiedSeconds,
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
        if (EchoDsdMetadata.isDsd(mimeType, displayName)) {
            val dsd = runCatching {
                contentResolver.openInputStream(uri)?.use(EchoDsdMetadata::read)
            }.getOrNull()
            if (dsd != null) {
                return DocumentAudioMetadata(
                    title = dsd.tags?.title?.takeIf { it.isNotBlank() } ?: fileTags?.title,
                    artist = dsd.tags?.artist?.takeIf { it.isNotBlank() } ?: fileTags?.artist,
                    album = dsd.tags?.album ?: fileTags?.album,
                    albumArtist = dsd.tags?.albumArtist ?: fileTags?.albumArtist,
                    durationMs = dsd.durationMs.coerceAtLeast(0L),
                    trackNumber = dsd.tags?.trackNumber ?: fileTags?.trackNumber,
                    discNumber = dsd.tags?.discNumber ?: fileTags?.discNumber,
                    year = dsd.tags?.year ?: fileTags?.year,
                    sampleRateHz = if (readSampleRate) dsd.sampleRateHz.takeIf { it > 0 } else null,
                )
            }
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
                    sampleRateHz = if (
                        readSampleRate &&
                        Build.VERSION.SDK_INT >= EchoPlatformCapabilities.MediaMetadataSampleRateSdk
                    ) {
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
        val lastModifiedMs: Long = 0L,
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
