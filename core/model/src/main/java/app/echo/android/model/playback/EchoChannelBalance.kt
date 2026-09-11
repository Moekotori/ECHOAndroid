package app.echo.android.model.playback

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class EchoChannelBalanceMonoMode(val id: String) {
    Off("off"),
    Sum("sum"),
    Left("left"),
    Right("right"),
    ;

    companion object {
        fun fromId(value: String?): EchoChannelBalanceMonoMode =
            entries.firstOrNull { it.id == value } ?: Off
    }
}

data class EchoChannelBalanceState(
    val enabled: Boolean = false,
    val balance: Float = 0f,
    val leftGainDb: Float = 0f,
    val rightGainDb: Float = 0f,
    val swapLeftRight: Boolean = false,
    val monoMode: EchoChannelBalanceMonoMode = EchoChannelBalanceMonoMode.Off,
    val constantPower: Boolean = true,
    val invertLeft: Boolean = false,
    val invertRight: Boolean = false,
    val leftBandGainsDb: List<Float> = EchoChannelBalance.zeroBands,
    val rightBandGainsDb: List<Float> = EchoChannelBalance.zeroBands,
    val leftDelayMs: Float = 0f,
    val rightDelayMs: Float = 0f,
    val processingSampleRateHz: Int? = null,
) {
    val normalized: EchoChannelBalanceState
        get() = copy(
            balance = EchoChannelBalance.clampBalance(balance),
            leftGainDb = EchoChannelBalance.clampGainDb(leftGainDb),
            rightGainDb = EchoChannelBalance.clampGainDb(rightGainDb),
            leftBandGainsDb = EchoChannelBalance.resizeBands(leftBandGainsDb),
            rightBandGainsDb = EchoChannelBalance.resizeBands(rightBandGainsDb),
            leftDelayMs = EchoChannelBalance.clampDelayMs(leftDelayMs),
            rightDelayMs = EchoChannelBalance.clampDelayMs(rightDelayMs),
        )

    val affectsSignal: Boolean
        get() = EchoChannelBalance.affectsSignal(this)

    val active: Boolean
        get() = enabled && affectsSignal

    val clippingRisk: Boolean
        get() = EchoChannelBalance.clippingRisk(this)
}

object EchoChannelBalance {
    const val MinBalance = -1f
    const val MaxBalance = 1f
    const val MinGainDb = -12f
    const val MaxGainDb = 6f
    const val BandCount = 3
    const val MinBandGainDb = -6f
    const val MaxBandGainDb = 3f
    const val MinDelayMs = 0f
    const val MaxDelayMs = 10f
    const val BalanceEpsilon = 0.001f
    const val GainEpsilonDb = 0.05f
    const val DelayEpsilonMs = 0.05f
    const val SmoothingSeconds = 0.02f
    const val SwitchSmoothingSeconds = 0.012f
    const val LowSplitHz = 200f
    const val HighSplitHz = 2_000f
    val zeroBands: List<Float> = listOf(0f, 0f, 0f)

    fun clampBalance(value: Float): Float =
        if (value.isFinite()) value.coerceIn(MinBalance, MaxBalance) else 0f

    fun clampGainDb(value: Float): Float =
        if (value.isFinite()) value.coerceIn(MinGainDb, MaxGainDb) else 0f

    fun clampBandGainDb(value: Float): Float =
        if (value.isFinite()) value.coerceIn(MinBandGainDb, MaxBandGainDb) else 0f

    fun clampDelayMs(value: Float): Float =
        if (value.isFinite()) value.coerceIn(MinDelayMs, MaxDelayMs) else 0f

    fun resizeBands(values: List<Float>): List<Float> =
        List(BandCount) { index -> clampBandGainDb(values.getOrElse(index) { 0f }) }

    fun dbToLinear(gainDb: Float): Float = 10.0.pow((gainDb / 20.0)).toFloat()

    fun linearToDb(gain: Float): Float =
        if (!gain.isFinite() || gain <= 1.0e-6f) -120f else (20.0 * kotlin.math.log10(gain.toDouble())).toFloat()

    fun maxDelaySamples(sampleRateHz: Int): Int =
        ceil(sampleRateHz.coerceAtLeast(1) * MaxDelayMs / 1000f).toInt().coerceAtLeast(1)

    fun affectsSignal(state: EchoChannelBalanceState): Boolean {
        val normalized = state.normalized
        return abs(normalized.balance) >= BalanceEpsilon ||
            abs(normalized.leftGainDb) >= GainEpsilonDb ||
            abs(normalized.rightGainDb) >= GainEpsilonDb ||
            normalized.swapLeftRight ||
            normalized.monoMode != EchoChannelBalanceMonoMode.Off ||
            normalized.invertLeft ||
            normalized.invertRight ||
            normalized.leftBandGainsDb.any { abs(it) >= GainEpsilonDb } ||
            normalized.rightBandGainsDb.any { abs(it) >= GainEpsilonDb } ||
            normalized.leftDelayMs >= DelayEpsilonMs ||
            normalized.rightDelayMs >= DelayEpsilonMs
    }

    fun shouldProcess(state: EchoChannelBalanceState): Boolean = state.enabled

    fun writeBalanceGains(
        balance: Float,
        leftGainDb: Float,
        rightGainDb: Float,
        dest: FloatArray,
        constantPower: Boolean = true,
    ) {
        val safeBalance = clampBalance(balance)
        val leftPan: Float
        val rightPan: Float
        if (!constantPower) {
            leftPan = if (safeBalance > 0f) 1f - safeBalance else 1f
            rightPan = if (safeBalance < 0f) 1f + safeBalance else 1f
        } else {
            val pan = (safeBalance + 1f) * (PI.toFloat() * 0.25f)
            val compensation = sqrt(2f)
            leftPan = min(1f, cos(pan) * compensation)
            rightPan = min(1f, sin(pan) * compensation)
        }
        dest[0] = leftPan * dbToLinear(clampGainDb(leftGainDb))
        dest[1] = rightPan * dbToLinear(clampGainDb(rightGainDb))
    }

    fun clippingRisk(state: EchoChannelBalanceState): Boolean {
        val gains = FloatArray(2)
        writeBalanceGains(state.balance, state.leftGainDb, state.rightGainDb, gains, state.constantPower)
        val leftBand = state.leftBandGainsDb.maxOrNull()?.let { dbToLinear(clampBandGainDb(it)) } ?: 1f
        val rightBand = state.rightBandGainsDb.maxOrNull()?.let { dbToLinear(clampBandGainDb(it)) } ?: 1f
        return gains[0] * leftBand > 1.02f || gains[1] * rightBand > 1.02f
    }

    fun applyFrameTo(
        left: Float,
        right: Float,
        leftGain: Float,
        rightGain: Float,
        swapLeftRight: Boolean,
        invertLeft: Boolean,
        invertRight: Boolean,
        monoMode: EchoChannelBalanceMonoMode,
        dest: FloatArray,
    ) {
        var outputLeft = left
        var outputRight = right
        if (swapLeftRight) {
            val swapped = outputLeft
            outputLeft = outputRight
            outputRight = swapped
        }
        if (invertLeft) outputLeft = -outputLeft
        if (invertRight) outputRight = -outputRight
        outputLeft *= leftGain
        outputRight *= rightGain
        applyMono(monoMode, outputLeft, outputRight, dest)
    }

    fun applyMono(mode: EchoChannelBalanceMonoMode, left: Float, right: Float, dest: FloatArray) {
        when (mode) {
            EchoChannelBalanceMonoMode.Sum -> {
                val mono = (left + right) * 0.5f
                dest[0] = mono
                dest[1] = mono
            }
            EchoChannelBalanceMonoMode.Left -> {
                dest[0] = left
                dest[1] = 0f
            }
            EchoChannelBalanceMonoMode.Right -> {
                dest[0] = 0f
                dest[1] = right
            }
            EchoChannelBalanceMonoMode.Off -> {
                dest[0] = left
                dest[1] = right
            }
        }
    }

    fun applyMonoTo(sample: Float, leftGain: Float, invertLeft: Boolean, dest: FloatArray) {
        val inverted = if (invertLeft) -sample else sample
        dest[0] = inverted * leftGain
    }

    fun onePoleAlpha(cutoffHz: Float, sampleRateHz: Float): Float {
        val safeRate = sampleRateHz.coerceAtLeast(1f)
        return (1f - exp((-2f * PI.toFloat() * cutoffHz) / safeRate)).coerceIn(0f, 1f)
    }

    fun applyBandCompensation(
        sample: Float,
        lowAlpha: Float,
        highAlpha: Float,
        bandGainsDb: FloatArray,
        lowState: FloatArray,
        highLowpassState: FloatArray,
    ): Float {
        lowState[0] += lowAlpha * (sample - lowState[0])
        highLowpassState[0] += highAlpha * (sample - highLowpassState[0])
        val low = lowState[0]
        val high = sample - highLowpassState[0]
        val mid = sample - low - high
        return low * dbToLinear(bandAt(bandGainsDb, 0)) +
            mid * dbToLinear(bandAt(bandGainsDb, 1)) +
            high * dbToLinear(bandAt(bandGainsDb, 2))
    }

    private fun bandAt(bands: FloatArray, index: Int): Float =
        clampBandGainDb(if (index in bands.indices) bands[index] else 0f)

    fun wrapDelayIndex(index: Int, length: Int): Int {
        if (length <= 0) return 0
        var wrapped = index % length
        if (wrapped < 0) wrapped += length
        return wrapped
    }

    fun readDelaySample(
        history: FloatArray,
        writeIndex: Int,
        delayMs: Float,
        sampleRateHz: Float,
    ): Float {
        if (history.isEmpty()) return 0f
        val delaySamples = clampDelayMs(delayMs) * sampleRateHz.coerceAtLeast(1f) / 1000f
        val whole = min(history.size - 2, floor(delaySamples).toInt()).coerceAtLeast(0)
        val fraction = delaySamples - whole
        val newer = history[wrapDelayIndex(writeIndex - whole, history.size)]
        val older = history[wrapDelayIndex(writeIndex - whole - 1, history.size)]
        return newer + (older - newer) * fraction
    }
}
