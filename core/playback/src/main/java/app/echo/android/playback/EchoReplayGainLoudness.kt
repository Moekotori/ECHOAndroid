package app.echo.android.playback

import kotlin.math.ln
import kotlin.math.roundToInt

object EchoReplayGainLoudness {
    const val TargetRateHz = 48_000
    const val ReferenceLufs = -18.0
    const val MinGainDb = -15.0
    const val MaxGainDb = 15.0

    fun gainDbFromMono48k(samples: FloatArray): Float? {
        val acc = EchoReplayGainAccumulator()
        samples.forEach(acc::add)
        return acc.gainDb()
    }

    fun formatGainTag(gainDb: Float): String {
        val hundredths = (gainDb * 100f).roundToInt() / 100.0
        return "%+.2f dB".format(java.util.Locale.US, hundredths)
    }
}

class EchoReplayGainAccumulator {
    private var preX1 = 0.0
    private var preX2 = 0.0
    private var preY1 = 0.0
    private var preY2 = 0.0
    private var rlbX1 = 0.0
    private var rlbX2 = 0.0
    private var rlbY1 = 0.0
    private var rlbY2 = 0.0
    private var sumSquares = 0.0
    private var count = 0L

    fun add(sample: Float) {
        val x = sample.toDouble()
        val y = PreB0 * x + PreB1 * preX1 + PreB2 * preX2 - PreA1 * preY1 - PreA2 * preY2
        preX2 = preX1
        preX1 = x
        preY2 = preY1
        preY1 = y
        val z = RlbB0 * y + RlbB1 * rlbX1 + RlbB2 * rlbX2 - RlbA1 * rlbY1 - RlbA2 * rlbY2
        rlbX2 = rlbX1
        rlbX1 = y
        rlbY2 = rlbY1
        rlbY1 = z
        sumSquares += z * z
        count++
    }

    fun gainDb(): Float? {
        if (count < EchoReplayGainLoudness.TargetRateHz / 10) return null
        val mean = sumSquares / count
        if (mean <= 1.0e-12) return EchoReplayGainLoudness.MaxGainDb.toFloat()
        val lufs = -0.691 + 10.0 * ln(mean) / Ln10
        return (EchoReplayGainLoudness.ReferenceLufs - lufs)
            .coerceIn(EchoReplayGainLoudness.MinGainDb, EchoReplayGainLoudness.MaxGainDb)
            .toFloat()
    }

    private companion object {
        const val Ln10 = 2.302585092994046
        const val PreB0 = 1.53512485958697
        const val PreB1 = -2.69169618940638
        const val PreB2 = 1.19839281085285
        const val PreA1 = -1.69065929318241
        const val PreA2 = 0.73248077421585
        const val RlbB0 = 1.0
        const val RlbB1 = -2.0
        const val RlbB2 = 1.0
        const val RlbA1 = -1.99004745483398
        const val RlbA2 = 0.99007225036621
    }
}
