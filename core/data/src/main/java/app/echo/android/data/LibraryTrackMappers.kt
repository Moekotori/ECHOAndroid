package app.echo.android.data

import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackMetadataUpdate
import app.echo.android.model.library.LibrarySource

fun LibraryTrackEntity.toEchoTrack(): EchoTrack =
    EchoTrack(
        id = id,
        uri = contentUri,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        artworkUri = artworkUri,
        durationMs = durationMs,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        sampleRateHz = sampleRateHz,
        dateModifiedSeconds = dateModifiedSeconds,
        source = LibrarySource(source),
        genre = genre,
        clipStartMs = clipStartMs,
        clipEndMs = clipEndMs,
    )

fun EchoTrack.toLibraryTrackEntity(): LibraryTrackEntity =
    LibraryTrackEntity(
        id = id,
        contentUri = uri,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        artworkUri = artworkUri,
        durationMs = durationMs,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        sampleRateHz = sampleRateHz,
        dateModifiedSeconds = dateModifiedSeconds,
        source = source.id,
        relativePath = null,
        metadataEditedAtEpochMs = null,
        lastSeenScanRunId = 0L,
        fingerprint = buildTrackFingerprint(this),
        clipStartMs = clipStartMs,
        clipEndMs = clipEndMs,
    ).withComputedSearchMetadata()

internal fun LibraryTrackEntity.withFingerprint(): LibraryTrackEntity =
    copy(fingerprint = if (fingerprint == LibraryScanPolicy.PendingDocumentMetadataFingerprint) fingerprint else buildTrackFingerprint(this))

internal fun LibraryTrackEntity.withScanMetadata(scanRunId: Long = lastSeenScanRunId): LibraryTrackEntity =
    copy(
        lastSeenScanRunId = scanRunId,
        fingerprint = if (fingerprint == LibraryScanPolicy.PendingDocumentMetadataFingerprint) fingerprint else buildTrackFingerprint(this),
    ).withComputedSearchMetadata()

internal fun LibraryTrackEntity.withComputedSearchMetadata(): LibraryTrackEntity {
    val nextNormalizedTitle = title.normalizedForSearch()
    val nextNormalizedArtist = artist.normalizedForSearch()
    val nextNormalizedAlbum = album?.normalizedForSearch()
    val nextNormalizedAlbumArtist = albumArtist?.normalizedForSearch()
    return copy(
        normalizedTitle = nextNormalizedTitle,
        normalizedArtist = nextNormalizedArtist,
        normalizedAlbum = nextNormalizedAlbum,
        normalizedAlbumArtist = nextNormalizedAlbumArtist,
        pinyinTitle = ChinesePinyin.toPinyin(title),
        pinyinArtist = ChinesePinyin.toPinyin(artist),
        pinyinAlbum = album?.let { ChinesePinyin.toPinyin(it) },
        albumKey = libraryAlbumKey(
            normalizedAlbum = LibraryAlbumGrouping.albumNameForKey(nextNormalizedAlbum),
            normalizedAlbumArtist = nextNormalizedAlbumArtist,
            normalizedArtist = nextNormalizedArtist,
        ),
        artistKey = libraryArtistKey(nextNormalizedArtist),
        genreKey = libraryGenreKey(genre?.normalizedForSearch()),
    )
}

internal fun LibraryTrackEntity.withUserMetadata(
    update: EchoTrackMetadataUpdate,
    editedAtEpochMs: Long,
): LibraryTrackEntity =
    copy(
        title = update.title.trim().ifBlank { title },
        artist = update.artist.trim().ifBlank { artist },
        album = update.album.normalizedNullableMetadata(),
        albumArtist = update.albumArtist.normalizedNullableMetadata(),
        artworkUri = update.artworkUri.normalizedNullableMetadata() ?: artworkUri,
        trackNumber = update.trackNumber?.takeIf { it > 0 },
        discNumber = update.discNumber?.takeIf { it > 0 },
        year = update.year?.takeIf { it > 0 },
        metadataEditedAtEpochMs = editedAtEpochMs,
    ).withScanMetadata()

internal fun LibraryTrackEntity.withPreservedUserMetadata(
    editedTrack: LibraryTrackEntity?,
): LibraryTrackEntity {
    if (editedTrack?.metadataEditedAtEpochMs == null) return this
    return copy(
        title = editedTrack.title,
        artist = editedTrack.artist,
        album = editedTrack.album,
        albumArtist = editedTrack.albumArtist,
        artworkUri = editedTrack.artworkUri,
        trackNumber = editedTrack.trackNumber,
        discNumber = editedTrack.discNumber,
        year = editedTrack.year,
        metadataEditedAtEpochMs = editedTrack.metadataEditedAtEpochMs,
    )
}

internal fun LibraryTrackEntity.prepareRemoteSyncTrack(
    editedTrack: LibraryTrackEntity?,
): LibraryTrackEntity =
    withPreservedUserMetadata(editedTrack).withScanMetadata(lastSeenScanRunId)

internal fun remapRemoteTrackIdentity(
    incoming: LibraryTrackEntity,
    existingByContentUri: Map<String, LibraryTrackEntity>,
): LibraryTrackEntity {
    val existing = existingByContentUri[incoming.contentUri] ?: return incoming
    if (existing.id == incoming.id) return incoming
    return incoming.copy(id = existing.id)
}

internal fun buildTrackFingerprint(track: EchoTrack): String =
    LibraryFingerprintPolicy.fingerprint(
        contentUri = track.uri,
        sizeBytes = track.sizeBytes,
        sampleRateHz = track.sampleRateHz,
        dateModifiedSeconds = track.dateModifiedSeconds,
        title = track.title,
        artist = track.artist,
        album = track.album,
        albumArtist = track.albumArtist,
        artworkUri = track.artworkUri,
        durationMs = track.durationMs,
        trackNumber = track.trackNumber,
        discNumber = track.discNumber,
        year = track.year,
        mimeType = track.mimeType,
        relativePath = null,
        remote = LibraryScanPolicy.isRemoteLibrarySource(track.source.id),
    )

internal fun buildTrackFingerprint(track: LibraryTrackEntity): String =
    LibraryFingerprintPolicy.fingerprint(
        contentUri = track.contentUri,
        sizeBytes = track.sizeBytes,
        sampleRateHz = track.sampleRateHz,
        dateModifiedSeconds = track.dateModifiedSeconds,
        title = track.title,
        artist = track.artist,
        album = track.album,
        albumArtist = track.albumArtist,
        artworkUri = track.artworkUri,
        durationMs = track.durationMs,
        trackNumber = track.trackNumber,
        discNumber = track.discNumber,
        year = track.year,
        mimeType = track.mimeType,
        relativePath = track.relativePath,
        remote = LibraryScanPolicy.isRemoteLibrarySource(track.source),
    )

internal fun LibraryTrackEntity.splitByCue(sheet: app.echo.android.model.library.CueSheet): List<LibraryTrackEntity> {
    val ended = app.echo.android.model.library.CueSheetPolicy.withEndTimes(sheet.tracks, durationMs)
    if (ended.size < 2) return listOf(this)
    return ended.map { cue ->
        val start = cue.startMs.coerceAtLeast(0L)
        val end = cue.endMs.takeIf { it > start } ?: 0L
        val length = if (end > start) end - start else (durationMs - start).coerceAtLeast(0L)
        copy(
            id = app.echo.android.model.library.CueSheetPolicy.cueTrackId(id, cue.number),
            contentUri = app.echo.android.model.library.CueSheetPolicy.contentUri(contentUri, cue.number),
            title = cue.title,
            artist = cue.performer ?: sheet.performer ?: artist,
            album = sheet.album ?: album,
            albumArtist = sheet.performer ?: albumArtist,
            durationMs = length,
            trackNumber = cue.number,
            year = sheet.year ?: year,
            genre = sheet.genre ?: genre,
            clipStartMs = start,
            clipEndMs = end,
        ).withComputedSearchMetadata().withFingerprint()
    }
}

internal fun String.normalizedForSearch(): String {
    val collapsed = java.text.Normalizer.normalize(trim(), java.text.Normalizer.Form.NFKC)
        .replace(AggregationWhitespace, " ")
        .lowercase()
        .trim()
    return if (LibraryMetadataSentinels.isUnknown(collapsed)) "" else collapsed
}

private val AggregationWhitespace = Regex("\\s+")

internal fun LibraryTrackEntity.toSummaryKeySet(): LibrarySummaryKeySet {
    val albumSummaryKey = albumKey.takeIf { it.isNotBlank() }?.let { key ->
        if (LibraryScanPolicy.isLocalLibrarySource(source)) {
            key
        } else {
            "remote||$source||$key"
        }
    }
    val artistSummaryKey = artistKey.takeIf {
        it.isNotBlank() && LibraryScanPolicy.isLocalLibrarySource(source)
    }
    val folderSummaryKey = if (LibraryScanPolicy.isLocalLibrarySource(source)) {
        relativePath?.takeIf { it.isNotBlank() }.orEmpty()
    } else {
        null
    }
    val genreSummaryKey = genreKey.takeIf {
        it.isNotBlank() && LibraryScanPolicy.isLocalLibrarySource(source)
    }
    return LibrarySummaryKeySet(
        albumKeys = setOfNotNull(albumSummaryKey),
        artistKeys = setOfNotNull(artistSummaryKey),
        folderKeys = setOfNotNull(folderSummaryKey),
        genreKeys = setOfNotNull(genreSummaryKey),
    )
}

internal fun Iterable<LibraryTrackEntity>.toSummaryKeySet(): LibrarySummaryKeySet =
    fold(LibrarySummaryKeySet()) { acc, track -> acc + track.toSummaryKeySet() }

private fun String?.normalizedNullableMetadata(): String? =
    this?.trim()?.takeIf { it.isNotBlank() }
