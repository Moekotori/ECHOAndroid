package app.echo.android.data

import app.echo.android.model.library.EchoTrack
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
        dateModifiedSeconds = dateModifiedSeconds,
        source = source.id,
        relativePath = null,
        lastSeenScanRunId = 0L,
        fingerprint = buildTrackFingerprint(this),
        normalizedTitle = title.normalizedForSearch(),
        normalizedArtist = artist.normalizedForSearch(),
        normalizedAlbum = album?.normalizedForSearch(),
        normalizedAlbumArtist = albumArtist?.normalizedForSearch(),
    )

internal fun LibraryTrackEntity.withScanMetadata(scanRunId: Long = lastSeenScanRunId): LibraryTrackEntity =
    copy(
        lastSeenScanRunId = scanRunId,
        fingerprint = buildTrackFingerprint(this),
        normalizedTitle = title.normalizedForSearch(),
        normalizedArtist = artist.normalizedForSearch(),
        normalizedAlbum = album?.normalizedForSearch(),
        normalizedAlbumArtist = albumArtist?.normalizedForSearch(),
    )

internal fun buildTrackFingerprint(track: EchoTrack): String =
    listOf(
        track.uri,
        track.sizeBytes.toString(),
        track.dateModifiedSeconds.toString(),
        track.title,
        track.artist,
        track.album.orEmpty(),
        track.albumArtist.orEmpty(),
        track.durationMs.toString(),
        track.trackNumber?.toString().orEmpty(),
        track.discNumber?.toString().orEmpty(),
        track.year?.toString().orEmpty(),
        track.mimeType.orEmpty(),
    ).joinToString("|")

internal fun buildTrackFingerprint(track: LibraryTrackEntity): String =
    listOf(
        track.contentUri,
        track.sizeBytes.toString(),
        track.dateModifiedSeconds.toString(),
        track.title,
        track.artist,
        track.album.orEmpty(),
        track.albumArtist.orEmpty(),
        track.durationMs.toString(),
        track.trackNumber?.toString().orEmpty(),
        track.discNumber?.toString().orEmpty(),
        track.year?.toString().orEmpty(),
        track.mimeType.orEmpty(),
        track.relativePath.orEmpty(),
    ).joinToString("|")

internal fun String.normalizedForSearch(): String =
    trim().lowercase()
