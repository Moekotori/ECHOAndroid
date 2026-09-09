package app.echo.android.playback

import kotlin.math.exp
import kotlin.math.sqrt

internal data class EchoSmartTransitionVocal(
    val curve: FloatArray = FloatArray(Buckets) { 0f },
    val confidence: Float = 0f,
) {
    val usable: Boolean
        get() = confidence >= MinimumConfidence

    companion object {
        const val Buckets = 9
        const val MinimumConfidence = 0.2f
    }
}

internal class EchoSmartTransitionVocalAccumulator(
    private val sampleRate: Int,
    private val channels: Int,
) {
    private val frameSize = maxOf(1, (sampleRate * 0.1f).toInt())
    private val highPassAlpha = 1f / (1f + (2f * Math.PI.toFloat() * 300f) / sampleRate)
    private val lowPassAlpha = 1f - exp((-2f * Math.PI.toFloat() * 3_400f) / sampleRate)
    private val centreRatios = ArrayList<Float>(160)
    private var previousMid = 0f
    private var previousSide = 0f
    private var midHigh = 0f
    private var sideHigh = 0f
    private var midBand = 0f
    private var sideBand = 0f
    private var centreEnergy = 0.0
    private var sideEnergy = 0.0
    private var inFrame = 0

    fun pushPcm16(left: Float, right: Float) {
        if (channels < 2) return
        val mid = (left + right) * 0.5f
        val side = (left - right) * 0.5f
        midHigh = highPassAlpha * (midHigh + mid - previousMid)
        sideHigh = highPassAlpha * (sideHigh + side - previousSide)
        previousMid = mid
        previousSide = side
        midBand += lowPassAlpha * (midHigh - midBand)
        sideBand += lowPassAlpha * (sideHigh - sideBand)
        centreEnergy += (midBand * midBand).toDouble()
        sideEnergy += (sideBand * sideBand).toDouble()
        inFrame += 1
        if (inFrame >= frameSize) flushFrame()
    }

    fun finish(): EchoSmartTransitionVocal {
        if (inFrame > 0) flushFrame()
        return EchoSmartTransitionVocalMath.fromCentreRatios(centreRatios)
    }

    private fun flushFrame() {
        val total = centreEnergy + sideEnergy
        centreRatios += if (total > 1e-12) (centreEnergy / total).toFloat() else 0f
        centreEnergy = 0.0
        sideEnergy = 0.0
        inFrame = 0
    }
}

internal object EchoSmartTransitionVocalMath {
    fun fromStereo(samples: FloatArray, sampleRate: Int): EchoSmartTransitionVocal {
        if (sampleRate <= 0 || samples.size < sampleRate * 2) return EchoSmartTransitionVocal()
        val accumulator = EchoSmartTransitionVocalAccumulator(sampleRate, 2)
        val frames = samples.size / 2
        for (frame in 0 until frames) {
            accumulator.pushPcm16(samples[frame * 2], samples[frame * 2 + 1])
        }
        return accumulator.finish()
    }

    fun conflict(
        current: EchoSmartTransitionVocal,
        next: EchoSmartTransitionVocal,
    ): Float {
        if (!current.usable || !next.usable) return 0f
        var weighted = 0f
        var weight = 0f
        val buckets = minOf(current.curve.size, next.curve.size)
        val confidence = minOf(current.confidence, next.confidence)
        for (index in 0 until buckets) {
            weighted += current.curve[index] * next.curve[index] * confidence
            weight += confidence
        }
        return if (weight > 0f) (weighted / weight).coerceIn(0f, 1f) else 0f
    }

    fun fromCentreRatios(ratios: List<Float>): EchoSmartTransitionVocal {
        if (ratios.size < 4) return EchoSmartTransitionVocal()
        val sorted = ratios.sorted()
        val low = percentile(sorted, 0.18f)
        val high = percentile(sorted, 0.86f)
        val spread = (high - low).coerceAtLeast(0f)
        val confidence = ((spread - 0.025f) / 0.18f).coerceIn(0f, 1f)
        if (confidence < 0.08f) return EchoSmartTransitionVocal()
        val activity = ratios.map { value ->
            ((value - (low + spread * 0.32f)) / maxOf(0.02f, spread * 0.48f)).coerceIn(0f, 1f)
        }
        return EchoSmartTransitionVocal(
            curve = bucketUpperMean(activity, EchoSmartTransitionVocal.Buckets),
            confidence = (Math.round(confidence * 1000f) / 1000f),
        )
    }

    private fun percentile(sorted: List<Float>, position: Float): Float {
        if (sorted.isEmpty()) return 0f
        val index = ((sorted.size - 1) * position).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private fun bucketUpperMean(values: List<Float>, buckets: Int): FloatArray {
        val result = FloatArray(buckets)
        for (bucket in 0 until buckets) {
            val start = values.size * bucket / buckets
            val end = maxOf(start + 1, values.size * (bucket + 1) / buckets)
            val window = values.subList(start, minOf(end, values.size)).sortedDescending()
            val keep = maxOf(1, (window.size + 1) / 2)
            result[bucket] = window.take(keep).average().toFloat()
        }
        return result
    }
}
