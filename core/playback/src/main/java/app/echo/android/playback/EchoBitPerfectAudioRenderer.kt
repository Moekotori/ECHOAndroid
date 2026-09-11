package app.echo.android.playback

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.CryptoConfig
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.SimpleDecoderOutputBuffer
import androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder
import androidx.media3.decoder.ffmpeg.FfmpegDecoderException
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.DecoderAudioRenderer
import app.echo.android.model.playback.EchoBitPerfectState

@UnstableApi
internal class EchoBitPerfectAudioRenderer(handler: Handler, listener: AudioRendererEventListener,
    private val sink: EchoBitPerfectAudioSink) :
    DecoderAudioRenderer<SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, FfmpegDecoderException>>(handler, listener, sink) {
    private var input = Format.Builder().build()
    override fun getName() = "EchoBitPerfectAudioRenderer"
    override fun supportsFormatInternal(format: Format): Int =
        if (EchoPlaybackProcessRuntime.usbBitPerfectEnabled && MimeTypes.isAudio(format.sampleMimeType))
            C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_TYPE

    override fun createDecoder(format: Format, cryptoConfig: CryptoConfig?): SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, FfmpegDecoderException> {
        input = format
        EchoPlaybackProcessRuntime.bitPerfectStatus = EchoBitPerfectSnapshot()
        if (format.cryptoType != C.CRYPTO_TYPE_NONE) unsupported()
        if (EchoDsdMime.isDecoderMime(format.sampleMimeType)) {
            if (EchoDsdDop.sampleRateHz(format.sampleRate) == null || format.channelCount !in 1..2) unsupported()
            return EchoDsdDopDecoder(format)
        }
        return when (format.sampleMimeType) {
            MimeTypes.AUDIO_FLAC, MimeTypes.AUDIO_ALAC -> FfmpegAudioDecoder(format, 4, 4,
                format.maxInputSize.takeIf { it > 0 } ?: 65536, C.ENCODING_PCM_32BIT)
            MimeTypes.AUDIO_RAW -> {
                if (format.pcmEncoding !in EchoBitPerfectAudioSink.INTEGER_ENCODINGS ||
                    EchoBitPerfectAudioSink.pcmBytes(format.pcmEncoding) !in 2..3) unsupported()
                RawPcmDecoder()
            }
            else -> unsupported()
        }
    }

    override fun getOutputFormat(decoder: SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, FfmpegDecoderException>): Format {
        if (decoder is EchoDsdDopDecoder) {
            sink.sourcePrecision(EchoDsdDop.SourceBits, dsdDop = true)
            return Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setPcmEncoding(C.ENCODING_PCM_24BIT)
                .setSampleRate(decoder.outputSampleRateHz)
                .setChannelCount(decoder.channelCount)
                .build()
        }
        if (decoder is FfmpegAudioDecoder) {
            val bits = decoder.sourceBitDepth
            if (bits !in listOf(16, 24)) unsupported()
            sink.sourcePrecision(bits)
            return Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setPcmEncoding(C.ENCODING_PCM_32BIT)
                .setSampleRate(decoder.sampleRate).setChannelCount(decoder.channelCount).build()
        }
        sink.sourcePrecision(EchoBitPerfectAudioSink.pcmBytes(input.pcmEncoding) * 8)
        return input
    }

    private fun unsupported(): Nothing {
        EchoPlaybackProcessRuntime.bitPerfectStatus = EchoBitPerfectSnapshot(EchoBitPerfectState.UnsupportedSource)
        throw FfmpegDecoderException("Strict USB supports unencrypted 16/24-bit WAV PCM, FLAC, ALAC, or DSD DoP")
    }
}

@UnstableApi
@Suppress("UNCHECKED_CAST")
private class RawPcmDecoder : SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, FfmpegDecoderException>(
    arrayOfNulls<DecoderInputBuffer>(4) as Array<DecoderInputBuffer>,
    arrayOfNulls<SimpleDecoderOutputBuffer>(4) as Array<SimpleDecoderOutputBuffer>) {
    override fun getName() = "EchoRawPcm"
    override fun createInputBuffer() = DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT)
    override fun createOutputBuffer() = SimpleDecoderOutputBuffer { releaseOutputBuffer(it) }
    override fun createUnexpectedDecodeException(error: Throwable) = FfmpegDecoderException("PCM copy failed", error)
    override fun decode(inputBuffer: DecoderInputBuffer, outputBuffer: SimpleDecoderOutputBuffer, reset: Boolean): FfmpegDecoderException? {
        val data = inputBuffer.data ?: return FfmpegDecoderException("Missing PCM")
        outputBuffer.init(inputBuffer.timeUs, data.remaining()).put(data).flip()
        return null
    }
}

/** Keep normal renderers available for mode changes, but never let strict playback fall back. */
@UnstableApi
internal class EchoNormalAudioRenderer(private val renderer: Renderer) : Renderer by renderer {
    private val gatedCapabilities = object : RendererCapabilities by renderer.capabilities {
        override fun supportsFormat(format: Format): Int =
            if (EchoPlaybackProcessRuntime.usbBitPerfectEnabled) C.FORMAT_UNSUPPORTED_TYPE
            else renderer.capabilities.supportsFormat(format)
    }
    override fun getCapabilities(): RendererCapabilities = gatedCapabilities
}
