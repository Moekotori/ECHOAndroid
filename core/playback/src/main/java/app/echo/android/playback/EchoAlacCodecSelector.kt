package app.echo.android.playback

import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

private const val QualcommAlacDecoder = "c2.qti.alac.sw.decoder"

/** The Qualcomm decoder can fail after initialization. Select an alternative before decoding. */
@UnstableApi
internal fun MediaCodecSelector.withAlacWorkaround(
    ffmpegAlacAvailable: () -> Boolean = { FfmpegLibrary.supportsFormat(MimeTypes.AUDIO_ALAC) },
): MediaCodecSelector = MediaCodecSelector { mime, secure, tunneling ->
    val decoders = getDecoderInfos(mime, secure, tunneling)
    if (mime == MimeTypes.AUDIO_ALAC && !secure && !tunneling &&
        decoders.any { it.name == QualcommAlacDecoder } && ffmpegAlacAvailable()
    ) {
        decoders.filterNot { it.name == QualcommAlacDecoder }
    } else {
        decoders
    }
}
