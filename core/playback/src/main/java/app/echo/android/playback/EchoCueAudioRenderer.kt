package app.echo.android.playback

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.CryptoConfig
import androidx.media3.decoder.Decoder
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoderOutputBuffer
import androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder
import androidx.media3.decoder.ffmpeg.FfmpegDecoderException
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DecoderAudioRenderer

/** CUEs require decoded PCM, including the valid suffix of a block containing the start point. */
@UnstableApi
internal class EchoCueAudioRenderer(handler: Handler, listener: AudioRendererEventListener, sink: AudioSink) :
    DecoderAudioRenderer<Decoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, FfmpegDecoderException>>(handler, listener, sink) {
    private var input = Format.Builder().build()
    override fun getName() = "EchoCueAudioRenderer"
    override fun supportsFormatInternal(format: Format): Int =
        if (!EchoPlaybackProcessRuntime.usbBitPerfectEnabled && handlesCue(format))
            C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_TYPE

    companion object {
        fun handlesCue(format: Format): Boolean {
            val source = (format.customData as? EchoCueFormat)?.source ?: return false
            return source.cryptoType == C.CRYPTO_TYPE_NONE &&
                (source.sampleMimeType == MimeTypes.AUDIO_RAW || FfmpegLibrary.supportsFormat(source.sampleMimeType ?: ""))
        }
    }

    override fun createDecoder(format: Format, cryptoConfig: CryptoConfig?): Decoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, FfmpegDecoderException> {
        input = format.cueInputFormat()
        val floatFormat = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setPcmEncoding(C.ENCODING_PCM_FLOAT)
            .setSampleRate(input.sampleRate).setChannelCount(input.channelCount).build()
        // Match the normal FFmpeg renderer: avoid float -> integer requantization in a transcoding sink.
        val encoding = if (getSinkFormatSupport(floatFormat) == AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY)
            C.ENCODING_PCM_FLOAT else C.ENCODING_PCM_16BIT
        return if (input.sampleMimeType == MimeTypes.AUDIO_RAW) RawPcmDecoder(input) else
            FfmpegAudioDecoder(input, 4, 4, input.maxInputSize.takeIf { it > 0 } ?: 65536, encoding, true)
    }

    override fun getOutputFormat(decoder: Decoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, FfmpegDecoderException>): Format =
        if (decoder is FfmpegAudioDecoder) Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(decoder.encoding).setSampleRate(decoder.sampleRate).setChannelCount(decoder.channelCount)
            .setCustomData(input.customData).build()
        else input
}
