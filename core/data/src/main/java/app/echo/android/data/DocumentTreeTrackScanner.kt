package app.echo.android.data

import app.echo.android.model.library.CueSheet
import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.LibraryScanOptions
import app.echo.android.model.platform.EchoPlatformCapabilities
import kotlinx.coroutines.CancellationException
import android.content.ContentResolver
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
        mediaStoreDuplicateKeys: LocalFileDuplicateIndex<LibraryTrackEntity> = LocalFileDuplicateIndex.empty(),
        readSampleRate: Boolean = true,
        refreshCueMetadata: Boolean = false,
        options: LibraryScanOptions = LibraryScanOptions(0L, 0L, false, false),
        onDuplicate: suspend (oldId: String, targetId: String) -> Unit = { _, _ -> },
        rejectedFiles: LocalScanFilterCache? = null,
        onSkipped: suspend () -> Unit = {},
        onUnchangedIds: suspend (List<String>) -> Unit = {},
        onRetryIds: suspend (List<String>) -> Unit = {},
        cueCache: CueScanCache? = null,
        listings: DocumentTreeListingCache? = null,
        reuseDirectoryListings: Boolean = true,
        persistArtwork: (ByteArray?) -> String? = { null },
        onFolderArtwork: suspend (List<String>, String) -> Unit = { _, _ -> },
        onBatch: suspend (List<LibraryTrackEntity>) -> Unit,
        onProgress: suspend (scannedCount: Int, currentTrack: LibraryTrackEntity?) -> Unit,
    ): MediaStoreScanOutcome {
        val folderArtwork = FolderArtworkResolver(contentResolver, persistArtwork)
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val safeBatchSize = batchSize.coerceAtLeast(1)
        val batch = ArrayList<LibraryTrackEntity>(safeBatchSize)
        val pendingDirectories = ArrayDeque<DocumentTreeDirectory>()
        var scannedCount = 0
        var unmatchedCueCount = 0
        val uncertainCueDirectories = ScanDirectoryIndex(ignoreCase = true)
        val descendantCues = HashMap<String, MutableList<Pair<CueSheet, String>>>()
        var descendantTrackCount = 0
        var querySucceeded = true
        var failedReads = 0
        var excludedDirectories = 0

        pendingDirectories.add(DocumentTreeDirectory(rootDocumentId, relativePath = "", lastModifiedMs = 0L))
        val treeKey = treeUri.toString()
        val cueChildrenByBase = LibraryScanPolicy.cueChildrenByBase(existingTracks.keys)
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
            val directoryCover = if (audioRows.isNotEmpty()) folderArtwork.documents(treeUri, directory.documentId, children) else null
            val songCovers = folderArtwork.documentSidecars(treeUri, children, audioRows.map { it.displayName })
            val inherited = descendantCues.remove(directory.relativePath).orEmpty()
            descendantTrackCount -= inherited.sumOf { it.first.tracks.size }
            val (cueByAudioName, unmatchedInDirectory) = cueSheetsByAudioName(
                cueRows, audioRows.map { it.displayName }, inherited,
                onReadFailure = {
                    failedReads++
                    uncertainCueDirectories.add(directory.relativePath)
                },
            ) { folder, sheet, cueName ->
                val target = appendRelativePath(directory.relativePath, folder)
                if (descendantTrackCount + sheet.tracks.size <= 32768) {
                    descendantCues.getOrPut(target) { ArrayList() }.add(sheet to cueName)
                    descendantTrackCount += sheet.tracks.size
                } else {
                    unmatchedCueCount++
                    failedReads++
                    uncertainCueDirectories.add(target)
                }
            }
            unmatchedCueCount += unmatchedInDirectory
            val unchangedIds = ArrayList<String>()
            val externalReadFailed = uncertainCueDirectories.contains(directory.relativePath)
            suspend fun preserveFailedCue(baseId: String) {
                failedReads++
                val ids = CueScanState.preservedIds(baseId, baseId in existingTracks, cueChildrenByBase[baseId].orEmpty())
                unchangedIds += ids
                onRetryIds(ids)
            }
            for (row in audioRows) {
                coroutineContext.ensureActive()
                val cueSheet = cueByAudioName[row.displayName]
                val duplicate = mediaStoreDuplicateKeys.find(
                    relativePath = row.relativePath,
                    sizeBytes = row.sizeBytes,
                    dateModifiedSeconds = row.lastModifiedMs.toEpochSeconds(),
                    displayName = row.displayName,
                )
                if (duplicate != null) {
                    val safBaseId = "saf:${Uri.encode(row.documentId)}"
                    val facts = cueSourceFacts(duplicate.id, duplicate.contentUri, row.sizeBytes, row.lastModifiedMs.toEpochSeconds(), row.relativePath, row.displayName)
                    val cached = cueCache?.reuse(facts, cueSheet, existingTracks, externalReadFailed)
                    if (cached != null) {
                        LibraryScanPolicy.safDuplicateAliases(safBaseId, cached, cueChildrenByBase[safBaseId].orEmpty())
                            .forEach { (oldId, targetId) -> onDuplicate(oldId, targetId) }
                        mediaStoreDuplicateKeys.otherCopies(duplicate.id).forEach { copy ->
                            LibraryScanPolicy.safDuplicateAliases(copy.id, cached, cueChildrenByBase[copy.id].orEmpty())
                                .forEach { (oldId, targetId) -> onDuplicate(oldId, targetId) }
                        }
                        unchangedIds += cached
                        scannedCount++
                        continue
                    }
                    val knownSource = listOf(safBaseId, duplicate.id).any {
                        it in existingTracks || cueChildrenByBase[it].orEmpty().isNotEmpty()
                    }
                    val expansion = if (externalReadFailed && knownSource) CueExpansion.ReadFailed else try {
                        val physical = row.documentUri.toTrackEntity(row.documentId, row.displayName, row.mimeType, row.sizeBytes,
                            row.lastModifiedMs, row.relativePath, null, readSampleRate, persistArtwork)
                            .let { if (it.artworkUri == null && directoryCover != null) it.copy(artworkUri = directoryCover) else it }
                            .copy(id = duplicate.id, source = duplicate.source)
                        if (physical.fingerprint == LibraryScanPolicy.PendingDocumentMetadataFingerprint && knownSource) CueExpansion.ReadFailed
                        else physical.expandLocalCue(contentResolver, cueSheet, row.displayName)
                    } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { CueExpansion.ReadFailed }
                    if (expansion is CueExpansion.ReadFailed) {
                        preserveFailedCue(safBaseId)
                        preserveFailedCue(duplicate.id)
                        mediaStoreDuplicateKeys.otherCopies(duplicate.id).forEach { preserveFailedCue(it.id) }
                        scannedCount++
                        onProgress(scannedCount, null)
                        continue
                    }
                    val expanded = (expansion as CueExpansion.Success).tracks.map { item ->
                        item.copy(contentUri = if (CueSheetPolicy.isCueTrackId(item.id))
                            CueSheetPolicy.contentUri(duplicate.contentUri, item.trackNumber ?: error("Missing CUE track number"))
                            else duplicate.contentUri).withFingerprint()
                    }
                    cueCache?.remember(facts, cueSheet, expanded)
                    LibraryScanPolicy.safDuplicateAliases(safBaseId, expanded.map { it.id }, cueChildrenByBase[safBaseId].orEmpty()).forEach { (oldId, targetId) ->
                        onDuplicate(oldId, targetId)
                    }
                    // 同一路径上的另一条曲库行（例如 mediastore:file）并进留下的 id，而不是当成另一首歌删掉。
                    mediaStoreDuplicateKeys.otherCopies(duplicate.id).forEach { copy ->
                        LibraryScanPolicy.safDuplicateAliases(copy.id, expanded.map { it.id }, cueChildrenByBase[copy.id].orEmpty())
                            .forEach { (oldId, targetId) -> onDuplicate(oldId, targetId) }
                    }
                    // Keep the stable MediaStore ID, including cue movements, and refresh changed tags.
                    expanded.forEach { item ->
                        batch += item
                        scannedCount += 1
                        onProgress(scannedCount, item)
                        if (batch.size >= safeBatchSize) {
                            onBatch(batch.toList())
                            batch.clear()
                        }
                    }
                    continue
                }
                val trackId = "saf:${Uri.encode(row.documentId)}"
                val facts = cueSourceFacts(trackId, row.documentUri.toString(), row.sizeBytes, row.lastModifiedMs.toEpochSeconds(), row.relativePath, row.displayName)
                val cached = cueCache?.reuse(facts, cueSheet, existingTracks, externalReadFailed)
                if (cached != null) {
                    unchangedIds += cached
                    scannedCount++
                    continue
                }
                val existingCueIds = cueChildrenByBase[trackId].orEmpty()
                val existingTrack = existingTracks[trackId] ?: existingCueIds.firstOrNull()?.let(existingTracks::get)
                val fileUnchanged = LibraryScanPolicy.shouldReuseUnchangedDocumentTrack(
                    existing = existingTrack?.copy(
                        contentUri = CueSheetPolicy.playbackUri(existingTrack.contentUri),
                    ),
                    incomingContentUri = row.documentUri.toString(),
                    incomingSizeBytes = row.sizeBytes,
                    incomingDateModifiedSeconds = row.lastModifiedMs.toEpochSeconds(),
                    incomingRelativePath = row.relativePath,
                )
                val remembered = if (fileUnchanged) {
                    LibraryScanPolicy.rememberUnchangedCueIds(
                        trackId, existingCueIds,
                        hasCueSheet = cueSheet != null || externalReadFailed || CueScanState.isWholeFileCue(existingTrack?.fingerprint) || (refreshCueMetadata),
                    )
                } else {
                    null
                }
                if (remembered != null) {
                    unchangedIds += remembered
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
                        // An unchanged file whose cue sheet appeared or disappeared still needs a real duration.
                        existingTrack = if (fileUnchanged) null else existingTrack,
                        readSampleRate = readSampleRate,
                        persistArtwork = persistArtwork,
                    )
                }.onSuccess { original ->
                    val cover = songCovers[row.displayName] ?: directoryCover
                    val track = if (original.artworkUri == null && cover != null) original.copy(artworkUri = cover) else original
                    // One unreadable file stays in the library and is retried. It must not block
                    // deletion of other songs that this listing proved are gone.
                    if (track.fingerprint == LibraryScanPolicy.PendingDocumentMetadataFingerprint) {
                        failedReads++
                        if (existingCueIds.isNotEmpty() || CueScanState.isWholeFileCue(existingTrack?.fingerprint)) {
                            preserveFailedCue(trackId)
                            scannedCount++
                            onProgress(scannedCount, null)
                            return@onSuccess
                        }
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
                    val expansion = track.expandLocalCue(contentResolver, cueSheet, row.displayName, externalReadFailed && existingTrack != null)
                    if (expansion is CueExpansion.ReadFailed) {
                        preserveFailedCue(trackId)
                        scannedCount++
                        onProgress(scannedCount, null)
                        return@onSuccess
                    }
                    cueCache?.remember(facts, cueSheet, (expansion as CueExpansion.Success).tracks)
                    (expansion as CueExpansion.Success).tracks.forEach { item ->
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
                    failedReads++
                    if (existingTrack != null) {
                        unchangedIds += trackId
                        unchangedIds += existingCueIds
                        onRetryIds(listOf(trackId) + existingCueIds)
                    }
                    Log.w(TAG, "Skipping unreadable document tree audio file.", error)
                }
            }
            // Assign same-name pictures per audio file, including cached CUE children and
            // the retained MediaStore alias. Directory fallback runs afterward, only for missing art.
            audioRows.forEach { row ->
                val cover = songCovers[row.displayName] ?: return@forEach
                val base = "saf:${Uri.encode(row.documentId)}"
                val duplicate = mediaStoreDuplicateKeys.find(row.relativePath, row.sizeBytes, row.lastModifiedMs.toEpochSeconds(), row.displayName)
                val ids = listOf(base) + cueChildrenByBase[base].orEmpty() +
                    listOfNotNull(duplicate?.id).flatMap { listOf(it) + cueChildrenByBase[it].orEmpty() }
                ids.chunked(256).forEach { onFolderArtwork(it, cover) }
            }
            if (directoryCover != null) {
                val ids = audioRows.flatMap { row ->
                    val base = "saf:${Uri.encode(row.documentId)}"
                    listOf(base) + cueChildrenByBase[base].orEmpty() +
                        listOfNotNull(mediaStoreDuplicateKeys.find(row.relativePath, row.sizeBytes, row.lastModifiedMs.toEpochSeconds(), row.displayName)?.id)
                            .flatMap { listOf(it) + cueChildrenByBase[it].orEmpty() }
                }
                ids.chunked(256).forEach { onFolderArtwork(it, directoryCover) }
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
            unmatchedCueCount = unmatchedCueCount + descendantCues.values.sumOf { it.size },
        )
    }

    private fun cueSheetsByAudioName(
        cueRows: List<DocumentAudioRow>,
        audioNames: List<String>,
        inherited: List<Pair<CueSheet, String>>,
        onReadFailure: () -> Unit,
        onDescendant: (String, CueSheet, String) -> Unit,
    ): Pair<Map<String, CueSheet>, Int> {
        val catalog = CueDirectoryCatalog(audioNames)
        val index = CueAudioNameIndex(audioNames)
        var unmatched = 0
        val local = ArrayList<Pair<CueSheet, String>>(inherited)
        for (row in cueRows) {
            val sheet = if (row.sizeBytes > CueSheetPolicy.MaxCueBytes) null else readCueSheet(row.documentUri) { reference ->
                val (folder, name) = cueReference(reference) ?: return@readCueSheet false
                folder.isEmpty() && index.match(name) != null
            }
            val directories = sheet?.forLocalDirectories().orEmpty()
            if (directories.isEmpty()) { unmatched++; onReadFailure(); continue }
            if (directories.values.sumOf { it.tracks.size } != sheet?.tracks?.size) onReadFailure()
            directories.forEach { (folder, localized) ->
                if (folder.isEmpty()) local += localized to row.displayName
                else onDescendant(folder, localized, row.displayName)
            }
        }
        for ((sheet, cueName) in local) {
            if (!catalog.add(sheet, cueName)) unmatched++
        }
        if (catalog.uncertain) onReadFailure()
        return catalog.sheets() to unmatched
    }

    private fun readCueSheet(uri: Uri, referenceExists: (String) -> Boolean): CueSheet? {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                readCueSheet(input, referenceExists)
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
        persistArtwork: (ByteArray?) -> String?,
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
        val metadata = readMetadata(this, readSampleRate, mimeType, displayName, persistArtwork)
        val title = localAudioTitle(metadata.title, displayName)
        val artist = metadata.artist.takeUnlessUnknownMetadata() ?: canonicalUnknownArtist()
        return LibraryTrackEntity(
            id = "saf:${Uri.encode(documentId)}",
            contentUri = toString(),
            title = title,
            fileName = displayName,
            artist = artist,
            album = metadata.album.takeUnlessUnknownMetadata(),
            albumArtist = metadata.albumArtist.takeUnlessUnknownMetadata(),
            artworkUri = metadata.artworkUri,
            durationMs = metadata.durationMs,
            trackNumber = metadata.trackNumber,
            discNumber = metadata.discNumber,
            year = metadata.year,
            genre = metadata.genre,
            composer = metadata.composer,
            musicRelationsJson = metadata.musicRelations.encoded(),
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
        persistArtwork: (ByteArray?) -> String?,
    ): DocumentAudioMetadata {
        val reads = ScanMetadataReads()
        val container = if (LocalAudioContainerMetadata.supports(mimeType, displayName)) {
            reads.optional({ contentResolver.openInputStream(uri) }, LocalAudioContainerMetadata::read)
        } else null
        val fileTags = mergeAudioTags(container?.tags, if (LocalAudioTagReadPolicy.prefersFileTags(mimeType, displayName)) {
            reads.optional({ contentResolver.openInputStream(uri) }, ::readLocalAudioTags)
        } else null)
        if (EchoDsdMetadata.isDsd(mimeType, displayName)) {
            val dsd = reads.optional({ contentResolver.openInputStream(uri) }, EchoDsdMetadata::read)
            if (dsd != null) {
                return DocumentAudioMetadata(
                    readSucceeded = !reads.failed,
                    title = dsd.tags?.title?.takeIf { it.isNotBlank() } ?: fileTags?.title,
                    artist = dsd.tags?.artist?.takeIf { it.isNotBlank() } ?: fileTags?.artist,
                    album = dsd.tags?.album ?: fileTags?.album,
                    albumArtist = dsd.tags?.albumArtist ?: fileTags?.albumArtist,
                    durationMs = dsd.durationMs.coerceAtLeast(0L),
                    trackNumber = dsd.tags?.trackNumber ?: fileTags?.trackNumber,
                    discNumber = dsd.tags?.discNumber ?: fileTags?.discNumber,
                    year = dsd.tags?.year ?: fileTags?.year,
                    genre = dsd.tags?.genre ?: fileTags?.genre,
                    composer = dsd.tags?.composer ?: fileTags?.composer,
                    musicRelations = (dsd.tags?.musicRelations ?: app.echo.android.model.library.EchoMusicRelationMetadata())
                        .withFallback(fileTags?.musicRelations ?: app.echo.android.model.library.EchoMusicRelationMetadata()),
                    sampleRateHz = if (readSampleRate) dsd.sampleRateHz.takeIf { it > 0 } else null,
                    artworkUri = persistArtwork(dsd.tags?.artworkBytes ?: fileTags?.artworkBytes),
                )
            }
        }
        fun fallbackMetadata() = fileTags.toDocumentMetadata(readSucceeded = !reads.failed && container?.durationMs?.let { it > 0 } == true).copy(
            durationMs = container?.durationMs ?: 0L,
            sampleRateHz = container?.sampleRateHz.takeIf { readSampleRate },
            artworkUri = persistArtwork(fileTags?.artworkBytes),
        )
        if (container != null && container.durationMs > 0 &&
            (EchoApeMetadata.isApe(mimeType, displayName) || LocalAudioContainerMetadata.isFlac(mimeType, displayName) ||
                displayName.substringAfterLast('.').lowercase(java.util.Locale.ROOT) in setOf("wv", "tta", "tak"))) {
            return fallbackMetadata()
        }
        val retriever = MediaMetadataRetriever()
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                retriever.setDataSource(descriptor.fileDescriptor)
                DocumentAudioMetadata(
                    readSucceeded = !reads.failed,
                    title = fileTags?.title?.takeIf { it.isNotBlank() }
                        ?: retriever.metadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    artist = fileTags?.artist?.takeIf { it.isNotBlank() }
                        ?: retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    album = fileTags?.album?.takeIf { it.isNotBlank() }
                        ?: retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    albumArtist = fileTags?.albumArtist?.takeIf { it.isNotBlank() }
                        ?: retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                    genre = fileTags?.genre?.takeIf { it.isNotBlank() }
                        ?: retriever.metadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
                    composer = fileTags?.composer?.takeIf { it.isNotBlank() }
                        ?: retriever.metadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER),
                    musicRelations = fileTags?.musicRelations ?: app.echo.android.model.library.EchoMusicRelationMetadata(),
                    durationMs = container?.durationMs?.takeIf { it > 0 } ?: retriever.metadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
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
                        container?.sampleRateHz ?: retriever.metadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                            ?.toIntOrNull()
                            ?.takeIf { it > 0 }
                    } else {
                        container?.sampleRateHz.takeIf { readSampleRate }
                    },
                    artworkUri = persistArtwork(fileTags?.artworkBytes ?: runCatching { retriever.embeddedPicture }.getOrNull()),
                )
            } ?: fallbackMetadata()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.d(TAG, "Unable to read document tree audio metadata for $uri.", error)
            fallbackMetadata()
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
            genre = this?.genre,
            composer = this?.composer,
            musicRelations = this?.musicRelations ?: app.echo.android.model.library.EchoMusicRelationMetadata(),
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
        val artworkUri: String? = null,
        val genre: String? = null,
        val composer: String? = null,
        val musicRelations: app.echo.android.model.library.EchoMusicRelationMetadata = app.echo.android.model.library.EchoMusicRelationMetadata(),
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
