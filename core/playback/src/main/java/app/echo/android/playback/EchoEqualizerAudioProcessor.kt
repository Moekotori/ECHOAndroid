package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.OpraEqBand
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
class EchoEqualizerAudioProcessor(
    private val onProcessingStateChanged: (app.echo.android.model.playback.EchoEqProcessingState) -> Unit = {},
    private val onProcessingFormatChanged: (Int?) -> Unit = {},
) : BaseAudioProcessor() {
    private var reportedState: app.echo.android.model.playback.EchoEqProcessingState? = null
    private fun reportState(state: app.echo.android.model.playback.EchoEqProcessingState) {
        if (reportedState != state) { reportedState = state; onProcessingStateChanged(state) }
    }
    private val pc = EchoPcEqRuntime(report = { rate, state -> reportProcessing(rate); reportState(state) })
    internal var preserveFloatHeadroom = false

    private var reportedSampleRate: Int? = null
    private fun reportProcessing(rate: Int?) {
        if (reportedSampleRate == rate) return
        reportedSampleRate = rate
        onProcessingFormatChanged(rate)
    }
    @Volatile
    private var runtime: EchoEqualizerRuntime = EchoEqualizerRuntime()

    // Preserve low-frequency accuracy at high sample rates in the compatibility path.
    private var coeffs = DoubleArray(0)
    private var delayLine = DoubleArray(0)
    private var configuredSampleRateHz: Int = 0
    private var configuredFilters: List<OpraEqBand>? = null
    @Volatile private var preparationSampleRateHz = 0
    @Volatile private var preparedFilters: PreparedFilters? = null

    private data class PreparedFilters(
        val filters: List<OpraEqBand>,
        val sampleRateHz: Int,
        val coefficients: DoubleArray,
    )

    fun setRuntime(runtime: EchoEqualizerRuntime) {
        // Control-thread edits prepare coefficients before publishing the new runtime. The
        // audio callback only adopts them; initial/changed stream formats have a fallback.
        val rate = preparationSampleRateHz
        if (runtime.shouldProcess && rate > 0) {
            val cached = preparedFilters
            if (cached?.filters != runtime.filters || cached.sampleRateHz != rate) {
                preparedFilters = prepareFilters(runtime.filters, rate)
            }
        }
        this.runtime = runtime
        pc.update(runtime)
    }

    override fun isActive(): Boolean = (runtime.shouldProcess || pc.fading) && super.isActive()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!isSupportedEncoding(inputAudioFormat.encoding)) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        configuredSampleRateHz = 0
        configuredFilters = null
        preparationSampleRateHz = inputAudioFormat.sampleRate
        pc.configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val output = replaceOutputBuffer(remaining)
        val format = inputAudioFormat
        val current = runtime
        if (!current.shouldProcess && !pc.fading) {
            reportProcessing(null)
            output.put(inputBuffer)
            output.flip()
            return
        }
        if (format.encoding == C.ENCODING_PCM_FLOAT && pc.available) {
            output.order(ByteOrder.nativeOrder()); output.put(inputBuffer); output.flip()
            val totalFrames = remaining / (format.channelCount * 4)
            var offsetFrames = 0
            while (offsetFrames < totalFrames) {
                val frames = minOf(1024, totalFrames - offsetFrames)
                output.position(offsetFrames * format.channelCount * 4)
                pc.process(output, frames)
                offsetFrames += frames
            }
            output.position(0)
            if (!preserveFloatHeadroom) for (i in 0 until output.remaining() / 4) output.putFloat(i * 4, output.getFloat(i * 4).coerceIn(-1f, 1f))
            return
        }
        if (current.filters.any { it.scope != app.echo.android.model.playback.EchoEqBandScope.Stereo || it.dynamicMode != app.echo.android.model.playback.EchoEqDynamicMode.Off } || current.audition.active) {
            reportState(app.echo.android.model.playback.EchoEqProcessingState.Failed)
            reportProcessing(null); output.put(inputBuffer); output.flip(); return
        }
        reportState(app.echo.android.model.playback.EchoEqProcessingState.Compatibility)
        ensureCoeffs(current.filters, format.sampleRate)
        reportProcessing(format.sampleRate)
        when (format.encoding) {
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_16BIT_BIG_ENDIAN,
            -> processPcm16(inputBuffer, output, format.channelCount, current.preampLinear)
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_24BIT_BIG_ENDIAN,
            -> processPcm24(inputBuffer, output, format.channelCount, current.preampLinear)
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_32BIT_BIG_ENDIAN,
            -> processPcm32(inputBuffer, output, format.channelCount, current.preampLinear)
            C.ENCODING_PCM_FLOAT -> processPcmFloat(inputBuffer, output, format.channelCount, current.preampLinear)
            else -> output.put(inputBuffer)
        }
        output.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        pc.flush()
        reportProcessing(null)
        delayLine.fill(0.0)
    }

    override fun onReset() {
        pc.close()
        reportProcessing(null)
        delayLine = DoubleArray(0)
        coeffs = DoubleArray(0)
        configuredSampleRateHz = 0
        configuredFilters = null
        preparationSampleRateHz = 0
        preparedFilters = null
    }

    private fun ensureCoeffs(filters: List<OpraEqBand>, sampleRateHz: Int) {
        if (configuredSampleRateHz == sampleRateHz && configuredFilters == filters) return
        val cached = preparedFilters
        val next = if (cached?.filters == filters && cached.sampleRateHz == sampleRateHz) cached
            else prepareFilters(filters, sampleRateHz)
        val previous = configuredFilters
        val sameTopology = configuredSampleRateHz == sampleRateHz && previous != null &&
            previous.size == filters.size && previous.indices.all { index ->
                previous[index].type == filters[index].type && previous[index].slope == filters[index].slope
            }
        coeffs = next.coefficients
        configuredSampleRateHz = sampleRateHz
        configuredFilters = filters
        val required = inputAudioFormat.channelCount.coerceAtLeast(1) * (coeffs.size / 5) * 2
        // Frequency/gain/Q drags retain filter history instead of restarting every buffer.
        if (!sameTopology || delayLine.size != required) delayLine = DoubleArray(required)
    }

    private fun prepareFilters(filters: List<OpraEqBand>, sampleRateHz: Int): PreparedFilters {
        val stages = filters.filter { it.enabled }.flatMap { EchoBiquadMath.stages(it, sampleRateHz.toFloat()) }
            .filter { it.a0.isFinite() && it.a0 != 0.0 }
        val packed = DoubleArray(stages.size * 5)
        stages.forEachIndexed { index, c ->
            val at = index * 5
            packed[at] = c.b0 / c.a0; packed[at + 1] = c.b1 / c.a0; packed[at + 2] = c.b2 / c.a0
            packed[at + 3] = c.a1 / c.a0; packed[at + 4] = c.a2 / c.a0
        }
        return PreparedFilters(filters, sampleRateHz, packed)
    }

    private fun processPcm16(
        inputBuffer: ByteBuffer,
        output: ByteBuffer,
        channelCount: Int,
        preampLinear: Float,
    ) {
        // Buffer views follow the input order; big-endian sources must not be written native.
        val order = inputBuffer.order()
        output.order(order)
        val shortIn = inputBuffer.asShortBuffer()
        val shortOut = output.asShortBuffer()
        val frameCount = shortIn.remaining() / channelCount
        ensureDelayLine(channelCount)
        repeat(frameCount) {
            for (channel in 0 until channelCount) {
                var sample = shortIn.get() / 32768f * preampLinear
                sample = filterSample(channel, sample)
                shortOut.put((sample.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.position(output.position() + frameCount * channelCount * 2)
    }

    private fun processPcm24(
        inputBuffer: ByteBuffer,
        output: ByteBuffer,
        channelCount: Int,
        preampLinear: Float,
    ) {
        val frameCount = inputBuffer.remaining() / (channelCount * 3)
        ensureDelayLine(channelCount)
        val order = inputBuffer.order()
        output.order(order)
        repeat(frameCount) {
            for (channel in 0 until channelCount) {
                var sample = inputBuffer.getPcm24(order) / 8_388_608f * preampLinear
                sample = filterSample(channel, sample)
                output.putPcm24(
                    (sample.coerceIn(-1f, 1f) * 8_388_607f).toInt().coerceIn(-8_388_608, 8_388_607),
                    order,
                )
            }
        }
    }

    private fun processPcm32(
        inputBuffer: ByteBuffer,
        output: ByteBuffer,
        channelCount: Int,
        preampLinear: Float,
    ) {
        val order = inputBuffer.order()
        output.order(order)
        val intIn = inputBuffer.asIntBuffer()
        val intOut = output.asIntBuffer()
        val frameCount = intIn.remaining() / channelCount
        ensureDelayLine(channelCount)
        repeat(frameCount) {
            for (channel in 0 until channelCount) {
                var sample = intIn.get() * (1.0f / 2_147_483_648f) * preampLinear
                sample = filterSample(channel, sample)
                intOut.put((sample.coerceIn(-1f, 1f) * 2_147_483_647f).toInt())
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.position(output.position() + frameCount * channelCount * 4)
    }

    private fun processPcmFloat(
        inputBuffer: ByteBuffer,
        output: ByteBuffer,
        channelCount: Int,
        preampLinear: Float,
    ) {
        val frameCount = inputBuffer.remaining() / (Float.SIZE_BYTES * channelCount)
        ensureDelayLine(channelCount)
        val floatIn = inputBuffer.asFloatBuffer()
        val floatOut = output.asFloatBuffer()
        repeat(frameCount) {
            for (channel in 0 until channelCount) {
                var sample = floatIn.get() * preampLinear
                sample = filterSample(channel, sample)
                floatOut.put(if (preserveFloatHeadroom) sample else sample.coerceIn(-1f, 1f))
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.position(output.position() + frameCount * channelCount * 4)
    }

    private fun filterSample(channel: Int, input: Float): Float {
        var sample = input.toDouble()
        val filters = coeffs
        val count = filters.size / 5
        val base = channel * count * 2
        for (index in 0 until count) {
            val at = index * 5
            val state = base + index * 2
            val y = filters[at] * sample + delayLine[state]
            delayLine[state] = filters[at + 1] * sample - filters[at + 3] * y + delayLine[state + 1]
            delayLine[state + 1] = filters[at + 2] * sample - filters[at + 4] * y
            sample = y
        }
        return sample.toFloat()
    }

    private fun ensureDelayLine(channelCount: Int) {
        val required = channelCount.coerceAtLeast(1) * (coeffs.size / 5) * 2
        if (delayLine.size != required) {
            delayLine = DoubleArray(required)
        }
    }

    private fun isSupportedEncoding(encoding: Int): Boolean =
        encoding == C.ENCODING_PCM_16BIT ||
            encoding == C.ENCODING_PCM_16BIT_BIG_ENDIAN ||
            encoding == C.ENCODING_PCM_24BIT ||
            encoding == C.ENCODING_PCM_24BIT_BIG_ENDIAN ||
            encoding == C.ENCODING_PCM_32BIT ||
            encoding == C.ENCODING_PCM_32BIT_BIG_ENDIAN ||
            encoding == C.ENCODING_PCM_FLOAT

    private fun ByteBuffer.getPcm24(order: ByteOrder): Int {
        val b0 = get().toInt() and 0xff
        val b1 = get().toInt() and 0xff
        val b2 = get().toInt() and 0xff
        val packed = if (order == ByteOrder.LITTLE_ENDIAN) {
            b0 or (b1 shl 8) or (b2 shl 16)
        } else {
            (b0 shl 16) or (b1 shl 8) or b2
        }
        return (packed shl 8) shr 8
    }

    private fun ByteBuffer.putPcm24(sample: Int, order: ByteOrder) {
        if (order == ByteOrder.LITTLE_ENDIAN) {
            put(sample.toByte())
            put((sample shr 8).toByte())
            put((sample shr 16).toByte())
        } else {
            put((sample shr 16).toByte())
            put((sample shr 8).toByte())
            put(sample.toByte())
        }
    }
}
