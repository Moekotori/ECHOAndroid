package app.echo.android.playback

import app.echo.android.model.playback.EchoEqFilterType
import app.echo.android.model.playback.OpraEqBand
import app.echo.android.model.playback.normalizedType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class EchoBiquadCoeffs(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a0: Double,
    val a1: Double,
    val a2: Double,
) {
    fun normalizedOrNull(): EchoBiquadNormalized? {
        if (a0 == 0.0 || !a0.isFinite()) return null
        val inv = 1.0 / a0
        return EchoBiquadNormalized(
            b0 = (b0 * inv).toFloat(),
            b1 = (b1 * inv).toFloat(),
            b2 = (b2 * inv).toFloat(),
            a1 = (a1 * inv).toFloat(),
            a2 = (a2 * inv).toFloat(),
        )
    }

    fun responseDb(frequencyHz: Float, sampleRateHz: Float): Float {
        val omega = digitalOmega(frequencyHz, sampleRateHz)
        val cos1 = cos(omega)
        val sin1 = sin(omega)
        val cos2 = cos(2.0 * omega)
        val sin2 = sin(2.0 * omega)
        val numeratorReal = b0 + b1 * cos1 + b2 * cos2
        val numeratorImaginary = -b1 * sin1 - b2 * sin2
        val denominatorReal = a0 + a1 * cos1 + a2 * cos2
        val denominatorImaginary = -a1 * sin1 - a2 * sin2
        val numeratorPower = numeratorReal * numeratorReal + numeratorImaginary * numeratorImaginary
        val denominatorPower = denominatorReal * denominatorReal + denominatorImaginary * denominatorImaginary
        if (numeratorPower <= 0.0 || denominatorPower <= 0.0) return 0f
        return (20.0 * log10(sqrt(numeratorPower / denominatorPower))).toFloat()
    }
}

data class EchoBiquadNormalized(
    val b0: Float,
    val b1: Float,
    val b2: Float,
    val a1: Float,
    val a2: Float,
)

object EchoBiquadMath {
    fun coefficients(band: OpraEqBand, sampleRateHz: Float): EchoBiquadCoeffs? {
        val frequencyHz = band.frequencyHz
        if (!frequencyHz.isFinite() || frequencyHz <= 0f || sampleRateHz <= 0f) return null
        val q = (band.q ?: band.slope)?.takeIf { it.isFinite() && it > 0f }
        return when (band.normalizedType()) {
            EchoEqFilterType.PeakDip -> peaking(
                frequencyHz = frequencyHz,
                sampleRateHz = sampleRateHz,
                gainDb = band.gainDb,
                q = q ?: 1f,
            )
            EchoEqFilterType.LowShelf -> shelf(
                highShelf = false,
                frequencyHz = frequencyHz,
                sampleRateHz = sampleRateHz,
                gainDb = band.gainDb,
                slope = q ?: 0.707f,
            )
            EchoEqFilterType.HighShelf -> shelf(
                highShelf = true,
                frequencyHz = frequencyHz,
                sampleRateHz = sampleRateHz,
                gainDb = band.gainDb,
                slope = q ?: 0.707f,
            )
            EchoEqFilterType.BandStop -> notch(
                frequencyHz = frequencyHz,
                sampleRateHz = sampleRateHz,
                q = q ?: 1f,
            )
            EchoEqFilterType.LowPass -> pass(
                highPass = false,
                frequencyHz = frequencyHz,
                sampleRateHz = sampleRateHz,
                q = q ?: 0.707f,
            )
            EchoEqFilterType.HighPass -> pass(
                highPass = true,
                frequencyHz = frequencyHz,
                sampleRateHz = sampleRateHz,
                q = q ?: 0.707f,
            )
            else -> null
        }
    }

    fun sampleCurveDb(
        filters: List<OpraEqBand>,
        frequencyHz: Float,
        sampleRateHz: Float = DefaultSampleRateHz,
        preampDb: Float = 0f,
    ): Float {
        var gainDb = preampDb
        filters.forEach { band ->
            val coeffs = coefficients(band, sampleRateHz) ?: return@forEach
            gainDb += coeffs.responseDb(frequencyHz, sampleRateHz)
        }
        return gainDb
    }

    private fun peaking(
        frequencyHz: Float,
        sampleRateHz: Float,
        gainDb: Float,
        q: Float,
    ): EchoBiquadCoeffs {
        val omega = digitalOmega(frequencyHz, sampleRateHz)
        val alpha = sin(omega) / (2.0 * q.coerceAtLeast(0.1f))
        val cosOmega = cos(omega)
        val a = 10.0.pow(gainDb / 40.0)
        return EchoBiquadCoeffs(
            b0 = 1.0 + alpha * a,
            b1 = -2.0 * cosOmega,
            b2 = 1.0 - alpha * a,
            a0 = 1.0 + alpha / a,
            a1 = -2.0 * cosOmega,
            a2 = 1.0 - alpha / a,
        )
    }

    private fun shelf(
        highShelf: Boolean,
        frequencyHz: Float,
        sampleRateHz: Float,
        gainDb: Float,
        slope: Float,
    ): EchoBiquadCoeffs {
        val omega = digitalOmega(frequencyHz, sampleRateHz)
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val a = 10.0.pow(gainDb / 40.0)
        val sqrtA = sqrt(a)
        val safeSlope = slope.coerceAtLeast(0.1f)
        val alpha = sinOmega / 2.0 * sqrt(((a + 1.0 / a) * (1.0 / safeSlope - 1.0) + 2.0).coerceAtLeast(0.0))
        return if (highShelf) {
            EchoBiquadCoeffs(
                b0 = a * ((a + 1.0) + (a - 1.0) * cosOmega + 2.0 * sqrtA * alpha),
                b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosOmega),
                b2 = a * ((a + 1.0) + (a - 1.0) * cosOmega - 2.0 * sqrtA * alpha),
                a0 = (a + 1.0) - (a - 1.0) * cosOmega + 2.0 * sqrtA * alpha,
                a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosOmega),
                a2 = (a + 1.0) - (a - 1.0) * cosOmega - 2.0 * sqrtA * alpha,
            )
        } else {
            EchoBiquadCoeffs(
                b0 = a * ((a + 1.0) - (a - 1.0) * cosOmega + 2.0 * sqrtA * alpha),
                b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosOmega),
                b2 = a * ((a + 1.0) - (a - 1.0) * cosOmega - 2.0 * sqrtA * alpha),
                a0 = (a + 1.0) + (a - 1.0) * cosOmega + 2.0 * sqrtA * alpha,
                a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosOmega),
                a2 = (a + 1.0) + (a - 1.0) * cosOmega - 2.0 * sqrtA * alpha,
            )
        }
    }

    private fun notch(
        frequencyHz: Float,
        sampleRateHz: Float,
        q: Float,
    ): EchoBiquadCoeffs {
        val omega = digitalOmega(frequencyHz, sampleRateHz)
        val alpha = sin(omega) / (2.0 * q.coerceAtLeast(0.1f))
        val cosOmega = cos(omega)
        return EchoBiquadCoeffs(
            b0 = 1.0,
            b1 = -2.0 * cosOmega,
            b2 = 1.0,
            a0 = 1.0 + alpha,
            a1 = -2.0 * cosOmega,
            a2 = 1.0 - alpha,
        )
    }

    private fun pass(
        highPass: Boolean,
        frequencyHz: Float,
        sampleRateHz: Float,
        q: Float,
    ): EchoBiquadCoeffs {
        val omega = digitalOmega(frequencyHz, sampleRateHz)
        val alpha = sin(omega) / (2.0 * q.coerceAtLeast(0.1f))
        val cosOmega = cos(omega)
        return if (highPass) {
            EchoBiquadCoeffs(
                b0 = (1.0 + cosOmega) / 2.0,
                b1 = -(1.0 + cosOmega),
                b2 = (1.0 + cosOmega) / 2.0,
                a0 = 1.0 + alpha,
                a1 = -2.0 * cosOmega,
                a2 = 1.0 - alpha,
            )
        } else {
            EchoBiquadCoeffs(
                b0 = (1.0 - cosOmega) / 2.0,
                b1 = 1.0 - cosOmega,
                b2 = (1.0 - cosOmega) / 2.0,
                a0 = 1.0 + alpha,
                a1 = -2.0 * cosOmega,
                a2 = 1.0 - alpha,
            )
        }
    }
}

internal fun digitalOmega(frequencyHz: Float, sampleRateHz: Float): Double {
    val safeFrequency = frequencyHz.coerceIn(1f, sampleRateHz * 0.49f)
    return 2.0 * PI * safeFrequency / sampleRateHz
}

internal const val DefaultEqSampleRateHz = 48_000f
private const val DefaultSampleRateHz = DefaultEqSampleRateHz
