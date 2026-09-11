package app.echo.android.playback

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.SimpleDecoderOutputBuffer
import androidx.media3.decoder.ffmpeg.FfmpegDecoderException

@UnstableApi
@Suppress("UNCHECKED_CAST")
internal class EchoDsdDopDecoder(
    format: Format,
) : SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, FfmpegDecoderException>(
    arrayOfNulls<DecoderInputBuffer>(4) as Array<DecoderInputBuffer>,
    arrayOfNulls<SimpleDecoderOutputBuffer>(4) as Array<SimpleDecoderOutputBuffer>,
) {
    val channelCount: Int = format.channelCount
    val outputSampleRateHz: Int = EchoDsdDop.sampleRateHz(format.sampleRate)
        ?: throw FfmpegDecoderException("Strict USB DoP supports DSD64 and DSD128 only")
    private val planar: Boolean = EchoDsdDop.isPlanar(format.sampleMimeType)
    private val lsbf: Boolean = EchoDsdDop.isLsbf(format.sampleMimeType)
    private var markerA: Boolean = true
    private var scratch = ByteArray(0)
    private var packed = ByteArray(0)

    init {
        if (channelCount !in 1..2) {
            throw FfmpegDecoderException("Strict USB DoP supports mono or stereo DSD")
        }
        setInitialInputBufferSize(format.maxInputSize.takeIf { it > 0 } ?: 8192)
    }

    override fun getName() = "EchoDsdDop"

    override fun createInputBuffer() = DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT)

    override fun createOutputBuffer() = SimpleDecoderOutputBuffer { releaseOutputBuffer(it) }

    override fun createUnexpectedDecodeException(error: Throwable) =
        FfmpegDecoderException("DSD DoP pack failed", error)

    override fun decode(
        inputBuffer: DecoderInputBuffer,
        outputBuffer: SimpleDecoderOutputBuffer,
        reset: Boolean,
    ): FfmpegDecoderException? {
        if (reset) markerA = true
        val data = inputBuffer.data ?: return FfmpegDecoderException("Missing DSD")
        val length = data.remaining()
        if (length <= 0 || EchoDsdDop.frameCount(length, channelCount) <= 0) {
            outputBuffer.shouldBeSkipped = true
            outputBuffer.init(inputBuffer.timeUs, 0)
            return null
        }
        if (scratch.size < length) scratch = ByteArray(length)
        data.get(scratch, 0, length)
        val outSize = EchoDsdDop.outputSize(length, channelCount)
        if (packed.size < outSize) packed = ByteArray(outSize)
        val result = EchoDsdDop.pack(
            source = scratch,
            sourceOffset = 0,
            sourceLength = length,
            channelCount = channelCount,
            planar = planar,
            lsbf = lsbf,
            startWithMarkerA = markerA,
            destination = packed,
        )
        markerA = result.nextMarkerA
        outputBuffer.init(inputBuffer.timeUs, result.bytesWritten).put(packed, 0, result.bytesWritten).flip()
        return null
    }
}
