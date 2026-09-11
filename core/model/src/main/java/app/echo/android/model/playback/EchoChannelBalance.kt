package app.echo.android.model.playback

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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
    val processingSampleRateHz: Int? = null,
) {
    val normalized: EchoChannelBalanceState
        get() = copy(
            balance = EchoChannelBalance.clampBalance(balance),
            leftGainDb = EchoChannelBalance.clampGainDb(leftGainDb),
            rightGainDb = EchoChannelBalance.clampGainDb(rightGainDb),
        )

    val affectsSignal: Boolean
        get() = EchoChannelBalance.affectsSignal(balance, leftGainDb, rightGainDb, swapLeftRight, monoMode)

    val active: Boolean
        get() = enabled && affectsSignal

    val clippingRisk: Boolean
        get() = EchoChannelBalance.clippingRisk(balance, leftGainDb, rightGainDb)
}

object EchoChannelBalance {
    const val MinBalance = -1f
    const val MaxBalance = 1f
    const val MinGainDb = -12f
    const val MaxGainDb = 6f
    const val BalanceEpsilon = 0.001f
    const val GainEpsilonDb = 0.05f
    const val SmoothingSeconds = 0.02f

    fun clampBalance(value: Float): Float =
        if (value.isFinite()) value.coerceIn(MinBalance, MaxBalance) else 0f

    fun clampGainDb(value: Float): Float =
        if (value.isFinite()) value.coerceIn(MinGainDb, MaxGainDb) else 0f

    fun dbToLinear(gainDb: Float): Float = 10.0.pow((clampGainDb(gainDb) / 20.0)).toFloat()

    fun affectsSignal(
        balance: Float,
        leftGainDb: Float,
        rightGainDb: Float,
        swapLeftRight: Boolean,
        monoMode: EchoChannelBalanceMonoMode,
    ): Boolean =
        abs(clampBalance(balance)) >= BalanceEpsilon ||
            abs(clampGainDb(leftGainDb)) >= GainEpsilonDb ||
            abs(clampGainDb(rightGainDb)) >= GainEpsilonDb ||
            swapLeftRight ||
            monoMode != EchoChannelBalanceMonoMode.Off

    fun shouldProcess(state: EchoChannelBalanceState): Boolean = state.active

    fun writeBalanceGains(balance: Float, leftGainDb: Float, rightGainDb: Float, dest: FloatArray) {
        val pan = (clampBalance(balance) + 1f) * (PI.toFloat() * 0.25f)
        val compensation = sqrt(2f)
        dest[0] = min(1f, cos(pan) * compensation) * dbToLinear(leftGainDb)
        dest[1] = min(1f, sin(pan) * compensation) * dbToLinear(rightGainDb)
    }

    fun clippingRisk(balance: Float, leftGainDb: Float, rightGainDb: Float): Boolean {
        val gains = FloatArray(2)
        writeBalanceGains(balance, leftGainDb, rightGainDb, gains)
        return gains[0] > 1.02f || gains[1] > 1.02f
    }

    fun applyFrameTo(
        left: Float,
        right: Float,
        leftGain: Float,
        rightGain: Float,
        swapLeftRight: Boolean,
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
        outputLeft *= leftGain
        outputRight *= rightGain
        when (monoMode) {
            EchoChannelBalanceMonoMode.Sum -> {
                val mono = (outputLeft + outputRight) * 0.5f
                outputLeft = mono
                outputRight = mono
            }
            EchoChannelBalanceMonoMode.Left -> outputRight = 0f
            EchoChannelBalanceMonoMode.Right -> outputLeft = 0f
            EchoChannelBalanceMonoMode.Off -> Unit
        }
        dest[0] = outputLeft
        dest[1] = outputRight
    }

    fun applyMonoTo(sample: Float, leftGain: Float, dest: FloatArray) {
        dest[0] = sample * leftGain
    }
}
