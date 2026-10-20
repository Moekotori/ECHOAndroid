package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.Decoder
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.SimpleDecoderOutputBuffer
import androidx.media3.decoder.ffmpeg.FfmpegDecoderException

/** Uses SimpleDecoder's bounded background worker, including a tail-drain input before EOS. */
@UnstableApi
internal class EchoDsdDopDecoder(format: Format) :
    Decoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, FfmpegDecoderException> {
    val channelCount = format.channelCount
    val outputSampleRateHz = EchoDsdDop.sampleRateHz(format.sampleRate)
        ?: throw FfmpegDecoderException("Strict USB DoP supports DSD64, DSD128, DSD256 and DSD512 only")
    private val worker: DopWorker
    private var eosPending = false
    private var eosTimeUs = 0L

    init {
        if (channelCount !in 1..2) throw FfmpegDecoderException("Strict USB DoP supports mono or stereo DSD")
        worker = DopWorker(format, outputSampleRateHz)
    }

    override fun getName() = "EchoDsdDop"
    override fun setOutputStartTimeUs(outputStartTimeUs: Long) = worker.setOutputStartTimeUs(outputStartTimeUs)
    override fun dequeueInputBuffer(): DecoderInputBuffer? = if (eosPending) null else worker.dequeueInputBuffer()
    override fun queueInputBuffer(inputBuffer: DecoderInputBuffer) {
        if (inputBuffer.isEndOfStream) {
            eosTimeUs = inputBuffer.timeUs
            inputBuffer.clearFlag(C.BUFFER_FLAG_END_OF_STREAM)
            (inputBuffer as DopInput).finish = true
            eosPending = true
        }
        worker.queueInputBuffer(inputBuffer)
    }
    override fun dequeueOutputBuffer(): SimpleDecoderOutputBuffer? {
        // Queue real EOS after the worker has processed the synthetic tail-drain input.
        // Input order is preserved even when all four worker buffers are occupied.
        if (eosPending) worker.dequeueInputBuffer()?.let { input ->
            input.timeUs = eosTimeUs
            input.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
            worker.queueInputBuffer(input)
            eosPending = false
        }
        return worker.dequeueOutputBuffer()
    }
    override fun flush() { eosPending = false; worker.flush() }
    override fun release() = worker.release()
}

@UnstableApi
private class DopInput : DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_DIRECT) {
    var finish = false
    override fun clear() { super.clear(); finish = false }
}

@UnstableApi
@Suppress("UNCHECKED_CAST")
private class DopWorker(format: Format, outputRateHz: Int) :
    SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, FfmpegDecoderException>(
        arrayOfNulls<DecoderInputBuffer>(4) as Array<DecoderInputBuffer>,
        arrayOfNulls<SimpleDecoderOutputBuffer>(4) as Array<SimpleDecoderOutputBuffer>,
    ) {
    private val channels = format.channelCount
    private val planar = EchoDsdDop.isPlanar(format.sampleMimeType)
    private val lsbf = EchoDsdDop.isLsbf(format.sampleMimeType)
    private val packer = EchoDsdDopStreamPacker(channels, outputRateHz)
    private var scratch = ByteArray(0)
    private var packed = ByteArray(channels * 3)

    init { setInitialInputBufferSize(format.maxInputSize.takeIf { it > 0 } ?: 8192) }
    override fun getName() = "EchoDsdDopWorker"
    override fun createInputBuffer(): DecoderInputBuffer = DopInput()
    override fun createOutputBuffer() = SimpleDecoderOutputBuffer { releaseOutputBuffer(it) }
    override fun createUnexpectedDecodeException(error: Throwable) = FfmpegDecoderException("DSD DoP pack failed", error)
    override fun decode(inputBuffer: DecoderInputBuffer, outputBuffer: SimpleDecoderOutputBuffer,
        reset: Boolean): FfmpegDecoderException? {
        if (reset) packer.reset()
        val written = if ((inputBuffer as DopInput).finish) packer.finish(packed) else {
            val data = inputBuffer.data ?: return FfmpegDecoderException("Missing DSD")
            val length = data.remaining()
            if (length % channels != 0) return FfmpegDecoderException("Incomplete DSD channel frame")
            if (scratch.size < length) scratch = ByteArray(length)
            data.get(scratch, 0, length)
            val outSize = maxOf(channels * 3, EchoDsdDop.outputSize(length, channels))
            if (packed.size < outSize) packed = ByteArray(outSize)
            packer.pack(scratch, length, planar, lsbf, inputBuffer.timeUs, packed)
        }
        outputBuffer.shouldBeSkipped = written == 0 || !isAtLeastOutputStartTimeUs(packer.outputTimeUs)
        outputBuffer.init(packer.outputTimeUs, written).put(packed, 0, written).flip()
        return null
    }
}
