package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.OpraEqBand
import java.nio.ByteBuffer

@UnstableApi
class EchoEqualizerAudioProcessor : BaseAudioProcessor() {
    @Volatile
    private var runtime: EchoEqualizerRuntime = EchoEqualizerRuntime()

    private var coeffs: Array<EchoBiquadNormalized> = emptyArray()
    private var delayLine: FloatArray = FloatArray(0)
    private var configuredSampleRateHz: Int = 0
    private var configuredFilterSignature: String = ""

    fun setRuntime(runtime: EchoEqualizerRuntime) {
        this.runtime = runtime
    }

    override fun isActive(): Boolean = runtime.shouldProcess && super.isActive()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!runtime.shouldProcess) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        configuredSampleRateHz = 0
        configuredFilterSignature = ""
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val output = replaceOutputBuffer(remaining)
        val format = inputAudioFormat
        val current = runtime
        if (!current.shouldProcess) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        ensureCoeffs(current.filters, format.sampleRate)
        when (format.encoding) {
            C.ENCODING_PCM_16BIT -> processPcm16(inputBuffer, output, format.channelCount, current.preampLinear)
            C.ENCODING_PCM_FLOAT -> processPcmFloat(inputBuffer, output, format.channelCount, current.preampLinear)
            else -> output.put(inputBuffer)
        }
        output.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        delayLine.fill(0f)
    }

    override fun onReset() {
        delayLine = FloatArray(0)
        coeffs = emptyArray()
        configuredSampleRateHz = 0
        configuredFilterSignature = ""
    }

    private fun ensureCoeffs(filters: List<OpraEqBand>, sampleRateHz: Int) {
        val signature = filters.joinToString(";") { band ->
            "${band.type}:${band.frequencyHz}:${band.gainDb}:${band.q}:${band.slope}"
        }
        if (configuredSampleRateHz == sampleRateHz && configuredFilterSignature == signature) return
        val nextCoeffs = filters.mapNotNull { band ->
            EchoBiquadMath.coefficients(band, sampleRateHz.toFloat())?.normalizedOrNull()
        }
        coeffs = nextCoeffs.toTypedArray()
        configuredSampleRateHz = sampleRateHz
        configuredFilterSignature = signature
        delayLine = FloatArray((inputAudioFormat.channelCount.coerceAtLeast(1) * coeffs.size * 2).coerceAtLeast(0))
    }

    private fun processPcm16(
        inputBuffer: ByteBuffer,
        output: ByteBuffer,
        channelCount: Int,
        preampLinear: Float,
    ) {
        val shortIn = inputBuffer.asShortBuffer()
        val shortOut = output.asShortBuffer()
        val frameCount = shortIn.remaining() / channelCount
        ensureDelayLine(channelCount)
        val filters = coeffs
        repeat(frameCount) {
            for (channel in 0 until channelCount) {
                var sample = shortIn.get() / 32768f * preampLinear
                sample = filterSample(channel, sample, filters)
                shortOut.put((sample.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.position(output.position() + frameCount * channelCount * 2)
    }

    private fun processPcmFloat(
        inputBuffer: ByteBuffer,
        output: ByteBuffer,
        channelCount: Int,
        preampLinear: Float,
    ) {
        val floatIn = inputBuffer.asFloatBuffer()
        val floatOut = output.asFloatBuffer()
        val frameCount = floatIn.remaining() / channelCount
        ensureDelayLine(channelCount)
        val filters = coeffs
        repeat(frameCount) {
            for (channel in 0 until channelCount) {
                var sample = floatIn.get() * preampLinear
                sample = filterSample(channel, sample, filters)
                floatOut.put(sample.coerceIn(-1f, 1f))
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.position(output.position() + frameCount * channelCount * 4)
    }

    private fun filterSample(
        channel: Int,
        input: Float,
        filters: Array<EchoBiquadNormalized>,
    ): Float {
        var sample = input
        val state = delayLine
        val stride = filters.size * 2
        val channelBase = channel * stride
        for (index in filters.indices) {
            val coeff = filters[index]
            val stateIndex = channelBase + index * 2
            val s1 = state[stateIndex]
            val s2 = state[stateIndex + 1]
            val y = coeff.b0 * sample + s1
            state[stateIndex] = coeff.b1 * sample - coeff.a1 * y + s2
            state[stateIndex + 1] = coeff.b2 * sample - coeff.a2 * y
            sample = y
        }
        return sample
    }

    private fun ensureDelayLine(channelCount: Int) {
        val required = channelCount.coerceAtLeast(1) * coeffs.size * 2
        if (delayLine.size != required) {
            delayLine = FloatArray(required)
        }
    }
}
