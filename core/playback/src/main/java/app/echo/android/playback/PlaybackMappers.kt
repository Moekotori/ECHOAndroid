package app.echo.android.playback

import android.net.Uri
import android.os.Bundle
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
import app.echo.android.model.playback.PlaybackQueueState

fun MediaItem.toEchoTrackRef(durationMs: Long = 0L): EchoTrackRef {
    val metadata = mediaMetadata
    val metadataDurationMs = metadata.durationMs?.takeIf { it > 0L } ?: 0L
    return EchoTrackRef(
        id = mediaId,
        uri = localConfiguration?.uri?.toString().orEmpty(),
        title = metadata.title?.toString().orEmpty().ifBlank { "Unknown Track" },
        artist = metadata.artist?.toString().orEmpty().ifBlank { "Unknown Artist" },
        album = metadata.albumTitle?.toString(),
        artworkUri = metadata.artworkUri?.toString(),
        durationMs = durationMs.takeIf { it > 0L } ?: metadataDurationMs,
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
                .also { builder ->
                    if (durationMs > 0L) builder.setDurationMs(durationMs)
                }
                .setExtras(embeddedArtworkExtras())
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

fun Player.toEchoPlaybackStatus(
    diagnostics: EchoPlaybackDiagnostics = toPlaybackDiagnosticsState().diagnostics,
): EchoPlaybackStatus {
    val metadata = toPlaybackMetadataState()
    val position = toPlaybackPositionState()
    val controls = toPlaybackControlsState()
    return EchoPlaybackStatus(
        state = controls.state,
        track = metadata.track,
        positionMs = position.positionMs,
        durationMs = position.durationMs,
        isPlaying = controls.isPlaying,
        repeatMode = controls.repeatMode,
        shuffleEnabled = controls.shuffleEnabled,
        playbackSpeed = controls.playbackSpeed,
        playbackPitch = controls.playbackPitch,
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
    val safeDuration = duration.takeIf { it > 0L } ?: 0L
    return PlaybackPositionState(
        positionMs = currentPosition.coerceAtLeast(0L),
        durationMs = safeDuration.coerceAtLeast(0L),
        bufferedMs = (bufferedPosition - currentPosition).coerceAtLeast(0L),
    )
}

fun playbackSessionPersistSignature(
    currentIndex: Int,
    playWhenReady: Boolean,
    mediaIds: Iterable<String>,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    playbackSpeed: Float,
    playbackPitch: Float,
): String {
    val ids = mediaIds.toList()
    return buildString(capacity = 32 + ids.size * 24) {
        append(currentIndex)
        append('|')
        append(playWhenReady)
        append('|')
        append(shuffleEnabled)
        append('|')
        append(repeatMode)
        append('|')
        append(playbackSpeed)
        append('|')
        append(playbackPitch)
        append('|')
        for (id in ids) {
            append(id)
            append(';')
        }
    }
}

fun Player.playbackQueueSignature(): String =
    playbackSessionPersistSignature(
        currentIndex = currentMediaItemIndex.takeIf { it in 0 until mediaItemCount } ?: -1,
        playWhenReady = playWhenReady,
        mediaIds = (0 until mediaItemCount).map { getMediaItemAt(it).mediaId },
        shuffleEnabled = shuffleModeEnabled,
        repeatMode = repeatMode,
        playbackSpeed = playbackParameters.speed,
        playbackPitch = playbackParameters.pitch,
    )

fun Player.toPlaybackQueueState(): PlaybackQueueState {
    val currentIndex = currentMediaItemIndex.takeIf { it in 0 until mediaItemCount } ?: -1
    val currentDurationMs = duration.takeIf { it > 0L } ?: 0L
    val items = (0 until mediaItemCount).map { index ->
        getMediaItemAt(index).toEchoTrackRef(
            durationMs = if (index == currentIndex) currentDurationMs else 0L,
        )
    }
    return PlaybackQueueState(
        items = items,
        currentIndex = currentIndex,
    )
}

fun Player.toPlaybackControlsState(): PlaybackControlsState =
    PlaybackControlsState(
        state = toEchoPlaybackState(),
        isPlaying = isPlaying,
        repeatMode = repeatMode.toEchoRepeatMode(),
        shuffleEnabled = shuffleModeEnabled,
        playbackSpeed = playbackParameters.speed,
        playbackPitch = playbackParameters.pitch,
        canSkipNext = hasNextMediaItem(),
        canSkipPrevious = hasPreviousMediaItem(),
        canSeek = isCurrentMediaItemSeekable,
    )

fun Player.toPlaybackDiagnosticsState(
    usbAudioStatus: EchoUsbAudioStatus = EchoUsbAudioStatus(),
    sourceSampleRateHz: Int? = null,
): PlaybackDiagnosticsState {
    val item = currentMediaItem
    val format = currentAudioFormat()
    val bitDepth = format?.takeIf { it.pcmEncoding != Format.NO_VALUE }
        ?.let { pcmBitDepth(it.pcmEncoding) }
    val decodedSampleRate = format?.sampleRate?.takeIf { it != Format.NO_VALUE }
    val sampleRate = sourceSampleRateHz?.takeIf { it > 0 } ?: decodedSampleRate
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
        decodedSampleRateHz = decodedSampleRate?.takeIf { it != sampleRate },
        channelCount = channels,
        bitDepth = bitDepth,
        bitrate = bitrate,
        bufferedMs = (bufferedPosition - currentPosition).coerceAtLeast(0L),
        requestToken = item?.mediaId?.hashCode()?.toLong() ?: 0L,
        lastCommand = if (isPlaying) "play" else "idle",
    ).withUsbAudioStatus(usbAudioStatus)
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

fun Player.toEchoPlaybackState(): EchoPlaybackState =
    echoPlaybackState(
        playbackState = playbackState,
        isPlaying = isPlaying,
        hasCurrentMediaItem = currentMediaItem != null,
    )

fun echoPlaybackState(
    playbackState: Int,
    isPlaying: Boolean,
    hasCurrentMediaItem: Boolean,
): EchoPlaybackState = when {
    playbackState == Player.STATE_IDLE && !hasCurrentMediaItem -> EchoPlaybackState.Idle
    playbackState == Player.STATE_IDLE -> EchoPlaybackState.Stopped
    playbackState == Player.STATE_BUFFERING -> EchoPlaybackState.Buffering
    playbackState == Player.STATE_ENDED -> EchoPlaybackState.Ended
    isPlaying -> EchoPlaybackState.Playing
    else -> EchoPlaybackState.Paused
}

fun Int.toEchoRepeatMode(): EchoRepeatMode = when (this) {
    Player.REPEAT_MODE_ALL -> EchoRepeatMode.All
    Player.REPEAT_MODE_ONE -> EchoRepeatMode.One
    else -> EchoRepeatMode.Off
}

private fun EchoTrackRef.embeddedArtworkExtras(): Bundle? {
    if (!artworkUri.isNullOrBlank() || uri.isBlank()) return null
    return Bundle().apply {
        putString(EchoEmbeddedArtworkSourceUriExtra, uri)
    }
}
