package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.EchoChannelBalance
import app.echo.android.model.playback.EchoChannelBalanceState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

@UnstableApi
class EchoChannelBalanceAudioProcessor(
    private val onProcessingFormatChanged: (Int?) -> Unit = {},
) : BaseAudioProcessor() {
    @Volatile
    private var runtime: EchoChannelBalanceState = EchoChannelBalanceState()

    private var reportedSampleRate: Int? = null
    private var smoothingSamples: Int = 1
    private var smoothedBalance: Float = 0f
    private var smoothedLeftGainDb: Float = 0f
    private var smoothedRightGainDb: Float = 0f
    private val gains = FloatArray(2)
    private val frame = FloatArray(2)

    fun setRuntime(state: EchoChannelBalanceState) {
        runtime = state.normalized
    }

    override fun isActive(): Boolean = EchoChannelBalance.shouldProcess(runtime) && super.isActive()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!EchoChannelBalance.shouldProcess(runtime)) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        if (!isSupportedEncoding(inputAudioFormat.encoding)) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        smoothingSamples = ((inputAudioFormat.sampleRate * EchoChannelBalance.SmoothingSeconds).toInt()).coerceAtLeast(1)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val output = replaceOutputBuffer(remaining)
        val format = inputAudioFormat
        val current = runtime
        if (!EchoChannelBalance.shouldProcess(current)) {
            reportProcessing(null)
            output.put(inputBuffer)
            output.flip()
            return
        }
        reportProcessing(format.sampleRate)
        when (format.encoding) {
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_16BIT_BIG_ENDIAN,
            -> processPcm16(inputBuffer, output, format.channelCount, current)
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_24BIT_BIG_ENDIAN,
            -> processPcm24(inputBuffer, output, format.channelCount, current)
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_32BIT_BIG_ENDIAN,
            -> processPcm32(inputBuffer, output, format.channelCount, current)
            C.ENCODING_PCM_FLOAT -> processPcmFloat(inputBuffer, output, format.channelCount, current)
            else -> output.put(inputBuffer)
        }
        output.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        reportProcessing(null)
        snapSmoothing(runtime)
    }

    override fun onReset() {
        reportProcessing(null)
        snapSmoothing(EchoChannelBalanceState())
    }

    private fun processPcm16(
        inputBuffer: ByteBuffer,
        output: ByteBuffer,
        channelCount: Int,
        state: EchoChannelBalanceState,
    ) {
        val shortIn = inputBuffer.asShortBuffer()
        val shortOut = output.asShortBuffer()
        val frameCount = shortIn.remaining() / channelCount.coerceAtLeast(1)
        repeat(frameCount) {
            advanceSmoothing(state)
            if (channelCount <= 1) {
                val sample = shortIn.get() / 32768f
                EchoChannelBalance.applyMonoTo(sample, gains[0], frame)
                shortOut.put((frame[0].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            } else {
                val left = shortIn.get() / 32768f
                val right = shortIn.get() / 32768f
                EchoChannelBalance.applyFrameTo(left, right, gains[0], gains[1], state.swapLeftRight, state.monoMode, frame)
                shortOut.put((frame[0].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                shortOut.put((frame[1].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                copyRemainingShorts(shortIn, shortOut, channelCount - 2)
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.position(output.position() + frameCount * channelCount * 2)
    }

    private fun processPcm24(
        inputBuffer: ByteBuffer,
        output: ByteBuffer,
        channelCount: Int,
        state: EchoChannelBalanceState,
    ) {
        val frameCount = inputBuffer.remaining() / (channelCount.coerceAtLeast(1) * 3)
        val order = inputBuffer.order()
        output.order(order)
        repeat(frameCount) {
            advanceSmoothing(state)
            if (channelCount <= 1) {
                val sample = inputBuffer.getPcm24(order) / 8_388_608f
                EchoChannelBalance.applyMonoTo(sample, gains[0], frame)
                output.putPcm24((frame[0].coerceIn(-1f, 1f) * 8_388_607f).toInt().coerceIn(-8_388_608, 8_388_607), order)
            } else {
                val left = inputBuffer.getPcm24(order) / 8_388_608f
                val right = inputBuffer.getPcm24(order) / 8_388_608f
                EchoChannelBalance.applyFrameTo(left, right, gains[0], gains[1], state.swapLeftRight, state.monoMode, frame)
                output.putPcm24((frame[0].coerceIn(-1f, 1f) * 8_388_607f).toInt().coerceIn(-8_388_608, 8_388_607), order)
                output.putPcm24((frame[1].coerceIn(-1f, 1f) * 8_388_607f).toInt().coerceIn(-8_388_608, 8_388_607), order)
                repeat(channelCount - 2) {
                    output.putPcm24(inputBuffer.getPcm24(order), order)
                }
            }
        }
    }

    private fun processPcm32(
        inputBuffer: ByteBuffer,
        output: ByteBuffer,
        channelCount: Int,
        state: EchoChannelBalanceState,
    ) {
        val intIn = inputBuffer.asIntBuffer()
        val intOut = output.asIntBuffer()
        val frameCount = intIn.remaining() / channelCount.coerceAtLeast(1)
        repeat(frameCount) {
            advanceSmoothing(state)
            if (channelCount <= 1) {
                val sample = intIn.get() * (1.0f / 2_147_483_648f)
                EchoChannelBalance.applyMonoTo(sample, gains[0], frame)
                intOut.put((frame[0].coerceIn(-1f, 1f) * 2_147_483_647f).toInt())
            } else {
                val left = intIn.get() * (1.0f / 2_147_483_648f)
                val right = intIn.get() * (1.0f / 2_147_483_648f)
                EchoChannelBalance.applyFrameTo(left, right, gains[0], gains[1], state.swapLeftRight, state.monoMode, frame)
                intOut.put((frame[0].coerceIn(-1f, 1f) * 2_147_483_647f).toInt())
                intOut.put((frame[1].coerceIn(-1f, 1f) * 2_147_483_647f).toInt())
                copyRemainingInts(intIn, intOut, channelCount - 2)
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.position(output.position() + frameCount * channelCount * 4)
    }

    private fun processPcmFloat(
        inputBuffer: ByteBuffer,
        output: ByteBuffer,
        channelCount: Int,
        state: EchoChannelBalanceState,
    ) {
        val floatIn = inputBuffer.asFloatBuffer()
        val floatOut = output.asFloatBuffer()
        val frameCount = floatIn.remaining() / channelCount.coerceAtLeast(1)
        repeat(frameCount) {
            advanceSmoothing(state)
            if (channelCount <= 1) {
                val sample = floatIn.get()
                EchoChannelBalance.applyMonoTo(sample, gains[0], frame)
                floatOut.put(frame[0].coerceIn(-1f, 1f))
            } else {
                val left = floatIn.get()
                val right = floatIn.get()
                EchoChannelBalance.applyFrameTo(left, right, gains[0], gains[1], state.swapLeftRight, state.monoMode, frame)
                floatOut.put(frame[0].coerceIn(-1f, 1f))
                floatOut.put(frame[1].coerceIn(-1f, 1f))
                copyRemainingFloats(floatIn, floatOut, channelCount - 2)
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.position(output.position() + frameCount * channelCount * 4)
    }

    private fun advanceSmoothing(state: EchoChannelBalanceState) {
        val steps = smoothingSamples.coerceAtLeast(1).toFloat()
        smoothedBalance = moveTowards(smoothedBalance, state.balance, (state.balance - smoothedBalance) / steps)
        smoothedLeftGainDb = moveTowards(smoothedLeftGainDb, state.leftGainDb, (state.leftGainDb - smoothedLeftGainDb) / steps)
        smoothedRightGainDb = moveTowards(smoothedRightGainDb, state.rightGainDb, (state.rightGainDb - smoothedRightGainDb) / steps)
        EchoChannelBalance.writeBalanceGains(smoothedBalance, smoothedLeftGainDb, smoothedRightGainDb, gains)
    }

    private fun snapSmoothing(state: EchoChannelBalanceState) {
        smoothedBalance = state.balance
        smoothedLeftGainDb = state.leftGainDb
        smoothedRightGainDb = state.rightGainDb
        EchoChannelBalance.writeBalanceGains(smoothedBalance, smoothedLeftGainDb, smoothedRightGainDb, gains)
    }

    private fun reportProcessing(rate: Int?) {
        if (reportedSampleRate == rate) return
        reportedSampleRate = rate
        onProcessingFormatChanged(rate)
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

    private companion object {
        fun moveTowards(current: Float, target: Float, step: Float): Float {
            if (abs(target - current) <= abs(step)) return target
            return current + if (target > current) abs(step) else -abs(step)
        }

        fun copyRemainingShorts(input: java.nio.ShortBuffer, output: java.nio.ShortBuffer, count: Int) {
            repeat(count.coerceAtLeast(0)) { output.put(input.get()) }
        }

        fun copyRemainingInts(input: java.nio.IntBuffer, output: java.nio.IntBuffer, count: Int) {
            repeat(count.coerceAtLeast(0)) { output.put(input.get()) }
        }

        fun copyRemainingFloats(input: java.nio.FloatBuffer, output: java.nio.FloatBuffer, count: Int) {
            repeat(count.coerceAtLeast(0)) { output.put(input.get()) }
        }
    }
}
