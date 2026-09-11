package app.echo.android.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibraryPlaybackSupport
import app.echo.android.model.playback.EchoDsdRates
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.playback.EchoLinkPlaybackUri
import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.playback.PlaybackControlsState
import app.echo.android.model.playback.PlaybackDiagnosticsState
import app.echo.android.model.playback.PlaybackMetadataState
import app.echo.android.model.playback.PlaybackPositionState
import app.echo.android.model.playback.PlaybackQueueState

fun MediaItem.toEchoTrackRef(durationMs: Long = 0L): EchoTrackRef {
    val metadata = mediaMetadata
    val metadataDurationMs = metadata.durationMs?.takeIf { it > 0L } ?: 0L
    val playUri = localConfiguration?.uri?.toString().orEmpty()
    val persistUri = metadata.extras?.getString(EchoPlaybackPersistUriExtra)
        ?: EchoLinkPlaybackUri.persistableUri(mediaId, playUri)
    val extrasSampleRate = metadata.extras?.getInt(EchoPlaybackSampleRateExtra, 0)?.takeIf { it > 0 }
    val extrasTrackNumber = metadata.extras?.getInt(EchoPlaybackTrackNumberExtra, 0)?.takeIf { it > 0 }
        ?: metadata.trackNumber?.takeIf { it > 0 }
    val extrasDiscNumber = metadata.extras?.getInt(EchoPlaybackDiscNumberExtra, 0)?.takeIf { it > 0 }
        ?: metadata.discNumber?.takeIf { it > 0 }
    return EchoTrackRef(
        id = mediaId,
        uri = persistUri,
        title = metadata.title?.toString().orEmpty().ifBlank { "Unknown Track" },
        artist = metadata.artist?.toString().orEmpty().ifBlank { "Unknown Artist" },
        album = metadata.albumTitle?.toString(),
        artworkUri = metadata.artworkUri?.toString(),
        durationMs = durationMs.takeIf { it > 0L } ?: metadataDurationMs,
        sampleRateHz = extrasSampleRate,
        trackNumber = extrasTrackNumber,
        discNumber = extrasDiscNumber,
        sourceId = metadata.extras?.getString(EchoPlaybackSourceExtra)?.takeIf { it.isNotBlank() },
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
                    trackNumber?.takeIf { it > 0 }?.let(builder::setTrackNumber)
                    discNumber?.takeIf { it > 0 }?.let(builder::setDiscNumber)
                }
                .setExtras(
                    playbackItemExtras(
                        playUri = uri,
                        persistUri = EchoLinkPlaybackUri.persistableUri(id, uri),
                        artworkUri = artworkUri,
                        sampleRateHz = sampleRateHz,
                        trackNumber = trackNumber,
                        discNumber = discNumber,
                        sourceId = sourceId,
                    ),
                )
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
        sampleRateHz = sampleRateHz,
        trackNumber = trackNumber,
        discNumber = discNumber,
        sourceId = source.id,
    )

fun EchoTrack.toMediaItem(): MediaItem {
    val persistUri = EchoLinkPlaybackUri.persistableUri(id, uri)
    return MediaItem.Builder()
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
                    trackNumber?.takeIf { it > 0 }?.let(builder::setTrackNumber)
                    discNumber?.takeIf { it > 0 }?.let(builder::setDiscNumber)
                }
                .setExtras(
                    playbackItemExtras(
                        playUri = uri,
                        persistUri = persistUri,
                        artworkUri = artworkUri,
                        sampleRateHz = sampleRateHz,
                        trackNumber = trackNumber,
                        discNumber = discNumber,
                        sourceId = source.id,
                    ),
                )
                .build(),
        )
        .build()
}

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
    outputRoute: EchoOutputRoute = EchoOutputRoute(),
): PlaybackDiagnosticsState {
    val item = currentMediaItem
    val format = currentAudioFormat()
    val bitDepth = format?.takeIf { it.pcmEncoding != Format.NO_VALUE }
        ?.let { pcmBitDepth(it.pcmEncoding) }
    val formatSampleRate = format?.sampleRate?.takeIf { it != Format.NO_VALUE }
    val channels = format?.channelCount?.takeIf { it != Format.NO_VALUE }
    val mediaUri = item?.localConfiguration?.uri?.toString() ?: item?.requestMetadata?.mediaUri?.toString()
    val readout = echoAudioFormatReadout(
        mimeType = format?.sampleMimeType,
        mediaUri = mediaUri,
        formatSampleRateHz = formatSampleRate,
        sourceSampleRateHz = sourceSampleRateHz,
        bitDepth = bitDepth,
        channelCount = channels,
        bitrate = listOf(format?.bitrate, format?.averageBitrate)
            .firstOrNull { it != null && it != Format.NO_VALUE },
    )
    val diagnostics = EchoPlaybackDiagnostics(
        codec = readout.codec,
        sampleRateHz = readout.sampleRateHz,
        decodedSampleRateHz = readout.decodedSampleRateHz,
        channelCount = readout.channelCount,
        bitDepth = readout.bitDepth,
        bitrate = readout.bitrate,
        bufferedMs = (bufferedPosition - currentPosition).coerceAtLeast(0L),
        requestToken = item?.mediaId?.hashCode()?.toLong() ?: 0L,
        lastCommand = if (isPlaying) "play" else "idle",
    ).withUsbAudioStatus(usbAudioStatus).withOutputRoute(outputRoute)
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

internal data class EchoAudioFormatReadout(
    val codec: String?,
    val sampleRateHz: Int?,
    val decodedSampleRateHz: Int?,
    val channelCount: Int?,
    val bitDepth: Int?,
    val bitrate: Int?,
)

internal fun echoAudioFormatReadout(
    mimeType: String?,
    mediaUri: String? = null,
    formatSampleRateHz: Int?,
    sourceSampleRateHz: Int?,
    bitDepth: Int?,
    channelCount: Int?,
    bitrate: Int?,
): EchoAudioFormatReadout {
    val mimeCodec = codecLabel(mimeType)
    val sourceRate = sourceSampleRateHz?.takeIf { it > 0 }
    val formatRate = formatSampleRateHz?.takeIf { it > 0 }
    val sourceIsDsd = mimeCodec == "DSD" ||
        EchoDsdMime.isDecoderMime(mimeType) ||
        LibraryPlaybackSupport.isDsd(mimeType, mediaUri) ||
        (sourceRate != null && EchoDsdRates.isDsdRate(sourceRate))
    val dsdRateHz = when {
        !sourceIsDsd -> null
        sourceRate != null && EchoDsdRates.isDsdRate(sourceRate) -> sourceRate
        formatRate != null && EchoDsdRates.isDsdRate(formatRate) -> formatRate
        formatRate != null -> EchoDsdRates.dsdRateFromDecoderPcm(formatRate)
        sourceRate != null -> EchoDsdRates.dsdRateFromDecoderPcm(sourceRate)
        else -> null
    }
    val sampleRate = dsdRateHz ?: sourceRate ?: formatRate
    val decodedRate = if (sourceIsDsd) {
        val dopRate = dsdRateHz?.let(EchoDsdRates::dopSampleRateHz)
        when {
            formatRate != null && dopRate != null && formatRate == dopRate -> formatRate
            formatRate != null && !EchoDsdRates.isDsdRate(formatRate) -> {
                val decoderPcm = formatRate
                if (dsdRateHz != null && decoderPcm == EchoDsdRates.decoderPcmRateHz(dsdRateHz)) {
                    EchoDsdRates.outputPcmRateHz(decoderPcm)
                } else {
                    decoderPcm
                }
            }
            dsdRateHz != null -> EchoDsdRates.outputPcmRateHz(EchoDsdRates.decoderPcmRateHz(dsdRateHz))
            else -> formatRate
        }
    } else {
        formatRate
    }
    val codec = if (sourceIsDsd) "DSD" else mimeCodec
    val resolvedBitrate = bitrate ?: run {
        if (codec == "PCM" && sampleRate != null && bitDepth != null && channelCount != null) {
            sampleRate * bitDepth * channelCount
        } else {
            null
        }
    }
    return EchoAudioFormatReadout(
        codec = codec,
        sampleRateHz = sampleRate,
        decodedSampleRateHz = decodedRate?.takeIf { it != sampleRate },
        channelCount = channelCount,
        bitDepth = bitDepth,
        bitrate = resolvedBitrate,
    )
}

private fun codecLabel(mime: String?): String? {
    if (mime.isNullOrBlank()) return null
    return when {
        mime.equals("audio/raw", ignoreCase = true) -> "PCM"
        mime.contains("flac", ignoreCase = true) -> "FLAC"
        mime.contains("alac", ignoreCase = true) -> "ALAC"
        mime.contains("wav", ignoreCase = true) -> "WAV"
        mime.contains("dsd", ignoreCase = true) ||
            mime.contains("dsf", ignoreCase = true) ||
            mime.contains("dff", ignoreCase = true) -> "DSD"
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

fun EchoRepeatMode.toPlayerRepeatMode(): Int = when (this) {
    EchoRepeatMode.All -> Player.REPEAT_MODE_ALL
    EchoRepeatMode.One -> Player.REPEAT_MODE_ONE
    EchoRepeatMode.Off -> Player.REPEAT_MODE_OFF
}

internal fun playbackItemExtras(
    playUri: String,
    persistUri: String,
    artworkUri: String?,
    sampleRateHz: Int? = null,
    trackNumber: Int? = null,
    discNumber: Int? = null,
    sourceId: String? = null,
): Bundle? {
    val extras = Bundle()
    if (persistUri.isNotBlank() && persistUri != playUri) {
        extras.putString(EchoPlaybackPersistUriExtra, persistUri)
    }
    if (artworkUri.isNullOrBlank() && playUri.isNotBlank()) {
        extras.putString(EchoEmbeddedArtworkSourceUriExtra, playUri)
    }
    sampleRateHz?.takeIf { it > 0 }?.let { extras.putInt(EchoPlaybackSampleRateExtra, it) }
    trackNumber?.takeIf { it > 0 }?.let { extras.putInt(EchoPlaybackTrackNumberExtra, it) }
    discNumber?.takeIf { it > 0 }?.let { extras.putInt(EchoPlaybackDiscNumberExtra, it) }
    sourceId?.takeIf { it.isNotBlank() }?.let { extras.putString(EchoPlaybackSourceExtra, it) }
    return extras.takeIf { !it.isEmpty }
}

internal const val EchoPlaybackPersistUriExtra = "app.echo.android.playback.PERSIST_URI"
internal const val EchoPlaybackSampleRateExtra = "app.echo.android.playback.SAMPLE_RATE_HZ"
internal const val EchoPlaybackTrackNumberExtra = "app.echo.android.playback.TRACK_NUMBER"
internal const val EchoPlaybackDiscNumberExtra = "app.echo.android.playback.DISC_NUMBER"
internal const val EchoPlaybackSourceExtra = "app.echo.android.playback.SOURCE"
