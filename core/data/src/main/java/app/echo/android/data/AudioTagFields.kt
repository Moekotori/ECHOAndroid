package app.echo.android.data

import app.echo.android.model.library.EchoTrackMetadataUpdate
import java.io.InputStream

data class AudioTagFields(
    val title: String,
    val artist: String,
    val album: String?,
    val albumArtist: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val lyrics: String? = null,
    val artworkBytes: ByteArray? = null,
    val artworkMime: String? = null,
    val replayGainTrackGainDb: Float? = null,
)

fun readLocalAudioTags(input: InputStream): AudioTagFields? =
    AudioFileTagRewriter.readFields(input)?.takeUnless { it.isBlank() }

internal fun EchoTrackMetadataUpdate.toAudioTagFields(): AudioTagFields =
    AudioTagFields(
        title = title.trim(),
        artist = artist.trim(),
        album = album?.trim()?.takeIf { it.isNotBlank() },
        albumArtist = albumArtist?.trim()?.takeIf { it.isNotBlank() },
        trackNumber = trackNumber?.takeIf { it > 0 },
        discNumber = discNumber?.takeIf { it > 0 },
        year = year?.takeIf { it > 0 },
    )

internal fun LibraryTrackEntity.toAudioTagFields(): AudioTagFields =
    AudioTagFields(
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
    )

internal fun AudioTagFields.isBlank(): Boolean =
    title.isBlank() &&
        artist.isBlank() &&
        album.isNullOrBlank() &&
        albumArtist.isNullOrBlank() &&
        trackNumber == null &&
        discNumber == null &&
        year == null

internal fun mergeAudioTags(preferred: AudioTagFields?, fallback: AudioTagFields?): AudioTagFields? {
    if (preferred == null || preferred.isBlank()) return fallback?.takeUnless { it.isBlank() }
    if (fallback == null || fallback.isBlank()) return preferred
    return AudioTagFields(
        title = preferred.title.ifBlank { fallback.title },
        artist = preferred.artist.ifBlank { fallback.artist },
        album = preferred.album?.takeIf { it.isNotBlank() } ?: fallback.album,
        albumArtist = preferred.albumArtist?.takeIf { it.isNotBlank() } ?: fallback.albumArtist,
        trackNumber = preferred.trackNumber ?: fallback.trackNumber,
        discNumber = preferred.discNumber ?: fallback.discNumber,
        year = preferred.year ?: fallback.year,
        lyrics = preferred.lyrics?.takeIf { it.isNotBlank() } ?: fallback.lyrics,
        artworkBytes = preferred.artworkBytes ?: fallback.artworkBytes,
        artworkMime = preferred.artworkMime ?: fallback.artworkMime,
        replayGainTrackGainDb = preferred.replayGainTrackGainDb ?: fallback.replayGainTrackGainDb,
    )
}

internal fun LibraryTrackEntity.withAudioTags(tags: AudioTagFields?): LibraryTrackEntity {
    val next = mergeAudioTags(preferred = tags, fallback = toAudioTagFields()) ?: return this
    val nextTitle = next.title.ifBlank { title }
    val nextArtist = next.artist.ifBlank { artist }
    if (
        nextTitle == title &&
        nextArtist == artist &&
        next.album == album &&
        next.albumArtist == albumArtist &&
        next.trackNumber == trackNumber &&
        next.discNumber == discNumber &&
        next.year == year
    ) {
        return this
    }
    return copy(
        title = nextTitle,
        artist = nextArtist,
        album = next.album,
        albumArtist = next.albumArtist,
        trackNumber = next.trackNumber,
        discNumber = next.discNumber,
        year = next.year,
    )
}

internal object LibraryWavTagPolicy {
    fun isWavContainer(mimeType: String?, fileNameOrUri: String? = null): Boolean {
        val mime = mimeType.orEmpty().lowercase()
        if (
            mime == "audio/wav" ||
            mime == "audio/x-wav" ||
            mime == "audio/wave" ||
            mime == "audio/vnd.wave"
        ) {
            return true
        }
        val name = fileNameOrUri.orEmpty().lowercase().substringBefore('?')
        return name.endsWith(".wav") || name.contains(".wav%")
    }
}
