package app.echo.android.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.playback.PlaybackControlsState
import app.echo.android.model.playback.PlaybackDiagnosticsState
import app.echo.android.model.playback.PlaybackMetadataState
import app.echo.android.model.playback.PlaybackPositionState

fun MediaItem.toEchoTrackRef(durationMs: Long = 0L): EchoTrackRef {
    val metadata = mediaMetadata
    return EchoTrackRef(
        id = mediaId,
        uri = localConfiguration?.uri?.toString().orEmpty(),
        title = metadata.title?.toString().orEmpty().ifBlank { "Unknown Track" },
        artist = metadata.artist?.toString().orEmpty().ifBlank { "Unknown Artist" },
        album = metadata.albumTitle?.toString(),
        artworkUri = metadata.artworkUri?.toString(),
        durationMs = durationMs.coerceAtLeast(0L),
    )
}

fun EchoTrackRef.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setUri(Uri.parse(uri))
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri?.let(Uri::parse))
                .build(),
        )
        .build()

fun EchoTrack.toEchoTrackRef(): EchoTrackRef =
    EchoTrackRef(
        id = id,
        uri = uri,
        title = title,
        artist = artist,
        album = album,
        artworkUri = artworkUri,
        durationMs = durationMs,
    )

fun EchoTrack.toMediaItem(): MediaItem =
    toEchoTrackRef().toMediaItem()

fun Player.toEchoPlaybackStatus(): EchoPlaybackStatus {
    val metadata = toPlaybackMetadataState()
    val position = toPlaybackPositionState()
    val controls = toPlaybackControlsState()
    val diagnostics = toPlaybackDiagnosticsState().diagnostics
    return EchoPlaybackStatus(
        state = controls.state,
        track = metadata.track,
        positionMs = position.positionMs,
        durationMs = position.durationMs,
        isPlaying = controls.isPlaying,
        repeatMode = controls.repeatMode,
        shuffleEnabled = controls.shuffleEnabled,
        diagnostics = diagnostics,
    )
}

fun Player.toPlaybackMetadataState(): PlaybackMetadataState {
    val item = currentMediaItem
    val safeDuration = duration.takeIf { it > 0L } ?: 0L
    val track = item?.toEchoTrackRef(durationMs = safeDuration)
    return PlaybackMetadataState(
        track = track,
        title = track?.title.orEmpty(),
        artist = track?.artist.orEmpty(),
        album = track?.album,
        artworkUri = track?.artworkUri,
        durationMs = track?.durationMs ?: 0L,
        mediaId = item?.mediaId,
    )
}

fun Player.toPlaybackPositionState(): PlaybackPositionState {
    val safeDuration = duration.takeIf { it > 0L }
        ?: currentMediaItem?.toEchoTrackRef(durationMs = 0L)?.durationMs
        ?: 0L
    return PlaybackPositionState(
        positionMs = currentPosition.coerceAtLeast(0L),
        durationMs = safeDuration.coerceAtLeast(0L),
        bufferedMs = (bufferedPosition - currentPosition).coerceAtLeast(0L),
        updateTimeEpochMs = System.currentTimeMillis(),
    )
}

fun Player.toPlaybackControlsState(): PlaybackControlsState =
    PlaybackControlsState(
        state = toEchoPlaybackState(),
        isPlaying = isPlaying,
        repeatMode = repeatMode.toEchoRepeatMode(),
        shuffleEnabled = shuffleModeEnabled,
        canSkipNext = hasNextMediaItem(),
        canSkipPrevious = hasPreviousMediaItem(),
        canSeek = isCurrentMediaItemSeekable,
    )

fun Player.toPlaybackDiagnosticsState(): PlaybackDiagnosticsState {
    val item = currentMediaItem
    val format = currentAudioFormat()
    val bitDepth = format?.takeIf { it.pcmEncoding != Format.NO_VALUE }
        ?.let { pcmBitDepth(it.pcmEncoding) }
    val sampleRate = format?.sampleRate?.takeIf { it != Format.NO_VALUE }
    val channels = format?.channelCount?.takeIf { it != Format.NO_VALUE }
    val codec = codecLabel(format?.sampleMimeType)
    val rawBitrate = listOf(format?.bitrate, format?.averageBitrate)
        .firstOrNull { it != null && it != Format.NO_VALUE }
    val bitrate = rawBitrate ?: run {
        if (codec == "PCM" && sampleRate != null && bitDepth != null && channels != null) {
            sampleRate * bitDepth * channels
        } else {
            null
        }
    }
    val diagnostics = EchoPlaybackDiagnostics(
        codec = codec,
        sampleRateHz = sampleRate,
        channelCount = channels,
        bitDepth = bitDepth,
        bitrate = bitrate,
        outputRoute = "Media3 / AudioTrack",
        bufferedMs = (bufferedPosition - currentPosition).coerceAtLeast(0L),
        requestToken = item?.mediaId?.hashCode()?.toLong() ?: 0L,
        lastCommand = if (isPlaying) "play" else "idle",
    )
    return PlaybackDiagnosticsState(
        diagnostics = diagnostics,
        lastError = diagnostics.lastError,
    )
}

private fun Player.currentAudioFormat(): Format? {
    val groups = currentTracks.groups
    for (group in groups) {
        if (group.type == C.TRACK_TYPE_AUDIO) {
            for (i in 0 until group.length) {
                if (group.isTrackSelected(i)) return group.getTrackFormat(i)
            }
        }
    }
    return null
}

private fun codecLabel(mime: String?): String? {
    if (mime.isNullOrBlank()) return null
    return when {
        mime.equals("audio/raw", ignoreCase = true) -> "PCM"
        mime.contains("flac", ignoreCase = true) -> "FLAC"
        mime.contains("alac", ignoreCase = true) -> "ALAC"
        mime.contains("wav", ignoreCase = true) -> "WAV"
        mime.contains("dsd", ignoreCase = true) || mime.contains("dsf", ignoreCase = true) -> "DSD"
        mime.contains("mpeg", ignoreCase = true) || mime.contains("mp3", ignoreCase = true) -> "MP3"
        mime.contains("mp4a", ignoreCase = true) || mime.contains("aac", ignoreCase = true) -> "AAC"
        mime.contains("opus", ignoreCase = true) -> "Opus"
        mime.contains("vorbis", ignoreCase = true) -> "Vorbis"
        mime.contains("ape", ignoreCase = true) -> "APE"
        else -> mime.substringAfter("audio/").uppercase()
    }
}

private fun pcmBitDepth(pcmEncoding: Int): Int? = when (pcmEncoding) {
    C.ENCODING_PCM_8BIT -> 8
    C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
    C.ENCODING_PCM_24BIT -> 24
    C.ENCODING_PCM_32BIT -> 32
    C.ENCODING_PCM_FLOAT -> 32
    else -> null
}

fun Player.toEchoPlaybackState(): EchoPlaybackState = when {
    playbackState == Player.STATE_IDLE && currentMediaItem == null -> EchoPlaybackState.Idle
    playbackState == Player.STATE_IDLE -> EchoPlaybackState.Stopped
    playbackState == Player.STATE_BUFFERING -> EchoPlaybackState.Buffering
    playbackState == Player.STATE_ENDED -> EchoPlaybackState.Ended
    isPlaying -> EchoPlaybackState.Playing
    playWhenReady -> EchoPlaybackState.Loading
    else -> EchoPlaybackState.Paused
}

fun Int.toEchoRepeatMode(): EchoRepeatMode = when (this) {
    Player.REPEAT_MODE_ALL -> EchoRepeatMode.All
    Player.REPEAT_MODE_ONE -> EchoRepeatMode.One
    else -> EchoRepeatMode.Off
}
