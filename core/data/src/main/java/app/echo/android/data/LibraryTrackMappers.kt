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
    ).withComputedSearchMetadata()

internal fun LibraryTrackEntity.withScanMetadata(scanRunId: Long = lastSeenScanRunId): LibraryTrackEntity =
    copy(
        lastSeenScanRunId = scanRunId,
        fingerprint = buildTrackFingerprint(this),
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
            normalizedAlbum = nextNormalizedAlbum,
            normalizedAlbumArtist = nextNormalizedAlbumArtist,
            normalizedArtist = nextNormalizedArtist,
        ),
        artistKey = libraryArtistKey(nextNormalizedArtist),
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

internal fun String.normalizedForSearch(): String =
    trim().lowercase()

private fun String?.normalizedNullableMetadata(): String? =
    this?.trim()?.takeIf { it.isNotBlank() }
