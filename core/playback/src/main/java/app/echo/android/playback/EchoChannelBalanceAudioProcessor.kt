package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.EchoChannelBalance
import app.echo.android.model.playback.EchoChannelBalanceMonoMode
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
    private var sampleRateHz: Float = 44_100f
    private var smoothingSamples: Int = 1
    private var switchSmoothingSamples: Int = 1
    private var smoothedBalance: Float = 0f
    private var smoothedLeftGainDb: Float = 0f
    private var smoothedRightGainDb: Float = 0f
    private val smoothedLeftBands = FloatArray(EchoChannelBalance.BandCount)
    private val smoothedRightBands = FloatArray(EchoChannelBalance.BandCount)
    private var smoothedLeftDelayMs: Float = 0f
    private var smoothedRightDelayMs: Float = 0f
    private var enabledMix: Float = 0f
    private var swapMix: Float = 0f
    private var invertLeftMix: Float = 0f
    private var invertRightMix: Float = 0f
    private var constantPowerMix: Float = 1f
    private var monoMix: Float = 1f
    private var previousMonoMode: EchoChannelBalanceMonoMode = EchoChannelBalanceMonoMode.Off
    private var targetMonoMode: EchoChannelBalanceMonoMode = EchoChannelBalanceMonoMode.Off
    private val linearGains = FloatArray(2)
    private val constantGains = FloatArray(2)
    private val gains = FloatArray(2)
    private val previousMono = FloatArray(2)
    private val activeMono = FloatArray(2)
    private val frame = FloatArray(2)
    private val leftLowState = FloatArray(1)
    private val rightLowState = FloatArray(1)
    private val leftHighLpState = FloatArray(1)
    private val rightHighLpState = FloatArray(1)
    private var delayLeft = FloatArray(1)
    private var delayRight = FloatArray(1)
    private var delayWriteIndex: Int = 0
    private var lowAlpha: Float = 0f
    private var highAlpha: Float = 0f

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
        sampleRateHz = inputAudioFormat.sampleRate.toFloat().coerceAtLeast(1f)
        smoothingSamples = (sampleRateHz * EchoChannelBalance.SmoothingSeconds).toInt().coerceAtLeast(1)
        switchSmoothingSamples = (sampleRateHz * EchoChannelBalance.SwitchSmoothingSeconds).toInt().coerceAtLeast(1)
        lowAlpha = EchoChannelBalance.onePoleAlpha(EchoChannelBalance.LowSplitHz, sampleRateHz)
        highAlpha = EchoChannelBalance.onePoleAlpha(EchoChannelBalance.HighSplitHz, sampleRateHz)
        ensureDelayBuffer(inputAudioFormat.sampleRate)
        snapSmoothing(runtime)
        enabledMix = if (runtime.enabled) 1f else 0f
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val output = replaceOutputBuffer(remaining)
        val format = inputAudioFormat
        val current = runtime
        if (!EchoChannelBalance.shouldProcess(current) && enabledMix <= 0.001f) {
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
        clearDelayAndBands()
    }

    override fun onReset() {
        reportProcessing(null)
        snapSmoothing(EchoChannelBalanceState())
        enabledMix = 0f
        clearDelayAndBands()
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
            if (channelCount <= 1) {
                processMonoSample(shortIn.get() / 32768f, state)
                shortOut.put((frame[0].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            } else {
                processStereoSample(shortIn.get() / 32768f, shortIn.get() / 32768f, state)
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
            if (channelCount <= 1) {
                processMonoSample(inputBuffer.getPcm24(order) / 8_388_608f, state)
                output.putPcm24((frame[0].coerceIn(-1f, 1f) * 8_388_607f).toInt().coerceIn(-8_388_608, 8_388_607), order)
            } else {
                processStereoSample(inputBuffer.getPcm24(order) / 8_388_608f, inputBuffer.getPcm24(order) / 8_388_608f, state)
                output.putPcm24((frame[0].coerceIn(-1f, 1f) * 8_388_607f).toInt().coerceIn(-8_388_608, 8_388_607), order)
                output.putPcm24((frame[1].coerceIn(-1f, 1f) * 8_388_607f).toInt().coerceIn(-8_388_608, 8_388_607), order)
                repeat(channelCount - 2) { output.putPcm24(inputBuffer.getPcm24(order), order) }
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
        val scale = 1.0f / 2_147_483_648f
        repeat(frameCount) {
            if (channelCount <= 1) {
                processMonoSample(intIn.get() * scale, state)
                intOut.put((frame[0].coerceIn(-1f, 1f) * 2_147_483_647f).toInt())
            } else {
                processStereoSample(intIn.get() * scale, intIn.get() * scale, state)
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
            if (channelCount <= 1) {
                processMonoSample(floatIn.get(), state)
                floatOut.put(frame[0].coerceIn(-1f, 1f))
            } else {
                processStereoSample(floatIn.get(), floatIn.get(), state)
                floatOut.put(frame[0].coerceIn(-1f, 1f))
                floatOut.put(frame[1].coerceIn(-1f, 1f))
                copyRemainingFloats(floatIn, floatOut, channelCount - 2)
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.position(output.position() + frameCount * channelCount * 4)
    }

    private fun processStereoSample(dryLeft: Float, dryRight: Float, state: EchoChannelBalanceState) {
        advanceSmoothing(state)
        val swappedLeft = dryLeft + (dryRight - dryLeft) * swapMix
        val swappedRight = dryRight + (dryLeft - dryRight) * swapMix
        val invertedLeft = swappedLeft * (1f - 2f * invertLeftMix)
        val invertedRight = swappedRight * (1f - 2f * invertRightMix)
        val wetLeft = EchoChannelBalance.applyBandCompensation(
            invertedLeft * gains[0],
            lowAlpha,
            highAlpha,
            smoothedLeftBands,
            leftLowState,
            leftHighLpState,
        )
        val wetRight = EchoChannelBalance.applyBandCompensation(
            invertedRight * gains[1],
            lowAlpha,
            highAlpha,
            smoothedRightBands,
            rightLowState,
            rightHighLpState,
        )
        EchoChannelBalance.applyMono(previousMonoMode, wetLeft, wetRight, previousMono)
        EchoChannelBalance.applyMono(targetMonoMode, wetLeft, wetRight, activeMono)
        val mixedLeft = previousMono[0] + (activeMono[0] - previousMono[0]) * monoMix
        val mixedRight = previousMono[1] + (activeMono[1] - previousMono[1]) * monoMix
        delayLeft[delayWriteIndex] = mixedLeft
        delayRight[delayWriteIndex] = mixedRight
        val delayedLeft = EchoChannelBalance.readDelaySample(delayLeft, delayWriteIndex, smoothedLeftDelayMs, sampleRateHz)
        val delayedRight = EchoChannelBalance.readDelaySample(delayRight, delayWriteIndex, smoothedRightDelayMs, sampleRateHz)
        delayWriteIndex = EchoChannelBalance.wrapDelayIndex(delayWriteIndex + 1, delayLeft.size)
        frame[0] = dryLeft + (delayedLeft - dryLeft) * enabledMix
        frame[1] = dryRight + (delayedRight - dryRight) * enabledMix
    }

    private fun processMonoSample(dry: Float, state: EchoChannelBalanceState) {
        advanceSmoothing(state)
        val inverted = dry * (1f - 2f * invertLeftMix)
        val wet = EchoChannelBalance.applyBandCompensation(
            inverted * gains[0],
            lowAlpha,
            highAlpha,
            smoothedLeftBands,
            leftLowState,
            leftHighLpState,
        )
        delayLeft[delayWriteIndex] = wet
        val delayed = EchoChannelBalance.readDelaySample(delayLeft, delayWriteIndex, smoothedLeftDelayMs, sampleRateHz)
        delayWriteIndex = EchoChannelBalance.wrapDelayIndex(delayWriteIndex + 1, delayLeft.size)
        frame[0] = dry + (delayed - dry) * enabledMix
        frame[1] = frame[0]
    }

    private fun advanceSmoothing(state: EchoChannelBalanceState) {
        val steps = smoothingSamples.coerceAtLeast(1).toFloat()
        val switchSteps = switchSmoothingSamples.coerceAtLeast(1).toFloat()
        if (targetMonoMode != state.monoMode) {
            previousMonoMode = targetMonoMode
            targetMonoMode = state.monoMode
            monoMix = 0f
        }
        smoothedBalance = moveTowards(smoothedBalance, state.balance, (state.balance - smoothedBalance) / steps)
        smoothedLeftGainDb = moveTowards(smoothedLeftGainDb, state.leftGainDb, (state.leftGainDb - smoothedLeftGainDb) / steps)
        smoothedRightGainDb = moveTowards(smoothedRightGainDb, state.rightGainDb, (state.rightGainDb - smoothedRightGainDb) / steps)
        for (band in 0 until EchoChannelBalance.BandCount) {
            val leftTarget = state.leftBandGainsDb.getOrElse(band) { 0f }
            val rightTarget = state.rightBandGainsDb.getOrElse(band) { 0f }
            smoothedLeftBands[band] = moveTowards(smoothedLeftBands[band], leftTarget, (leftTarget - smoothedLeftBands[band]) / steps)
            smoothedRightBands[band] = moveTowards(smoothedRightBands[band], rightTarget, (rightTarget - smoothedRightBands[band]) / steps)
        }
        smoothedLeftDelayMs = moveTowards(smoothedLeftDelayMs, state.leftDelayMs, (state.leftDelayMs - smoothedLeftDelayMs) / steps)
        smoothedRightDelayMs = moveTowards(smoothedRightDelayMs, state.rightDelayMs, (state.rightDelayMs - smoothedRightDelayMs) / steps)
        val enabledTarget = if (state.enabled) 1f else 0f
        enabledMix = moveTowards(enabledMix, enabledTarget, (enabledTarget - enabledMix) / switchSteps)
        swapMix = moveTowards(swapMix, if (state.swapLeftRight) 1f else 0f, ((if (state.swapLeftRight) 1f else 0f) - swapMix) / switchSteps)
        invertLeftMix = moveTowards(invertLeftMix, if (state.invertLeft) 1f else 0f, ((if (state.invertLeft) 1f else 0f) - invertLeftMix) / switchSteps)
        invertRightMix = moveTowards(invertRightMix, if (state.invertRight) 1f else 0f, ((if (state.invertRight) 1f else 0f) - invertRightMix) / switchSteps)
        constantPowerMix = moveTowards(constantPowerMix, if (state.constantPower) 1f else 0f, ((if (state.constantPower) 1f else 0f) - constantPowerMix) / switchSteps)
        monoMix = moveTowards(monoMix, 1f, (1f - monoMix) / switchSteps)
        EchoChannelBalance.writeBalanceGains(smoothedBalance, smoothedLeftGainDb, smoothedRightGainDb, linearGains, constantPower = false)
        EchoChannelBalance.writeBalanceGains(smoothedBalance, smoothedLeftGainDb, smoothedRightGainDb, constantGains, constantPower = true)
        gains[0] = linearGains[0] + (constantGains[0] - linearGains[0]) * constantPowerMix
        gains[1] = linearGains[1] + (constantGains[1] - linearGains[1]) * constantPowerMix
    }

    private fun snapSmoothing(state: EchoChannelBalanceState) {
        smoothedBalance = state.balance
        smoothedLeftGainDb = state.leftGainDb
        smoothedRightGainDb = state.rightGainDb
        for (band in 0 until EchoChannelBalance.BandCount) {
            smoothedLeftBands[band] = state.leftBandGainsDb.getOrElse(band) { 0f }
            smoothedRightBands[band] = state.rightBandGainsDb.getOrElse(band) { 0f }
        }
        smoothedLeftDelayMs = state.leftDelayMs
        smoothedRightDelayMs = state.rightDelayMs
        swapMix = if (state.swapLeftRight) 1f else 0f
        invertLeftMix = if (state.invertLeft) 1f else 0f
        invertRightMix = if (state.invertRight) 1f else 0f
        constantPowerMix = if (state.constantPower) 1f else 0f
        previousMonoMode = state.monoMode
        targetMonoMode = state.monoMode
        monoMix = 1f
        enabledMix = if (state.enabled) 1f else 0f
        EchoChannelBalance.writeBalanceGains(smoothedBalance, smoothedLeftGainDb, smoothedRightGainDb, gains, state.constantPower)
    }

    private fun ensureDelayBuffer(sampleRate: Int) {
        val length = EchoChannelBalance.maxDelaySamples(sampleRate) + 2
        if (delayLeft.size != length) {
            delayLeft = FloatArray(length)
            delayRight = FloatArray(length)
            delayWriteIndex = 0
        }
    }

    private fun clearDelayAndBands() {
        delayLeft.fill(0f)
        delayRight.fill(0f)
        delayWriteIndex = 0
        leftLowState[0] = 0f
        rightLowState[0] = 0f
        leftHighLpState[0] = 0f
        rightHighLpState[0] = 0f
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
