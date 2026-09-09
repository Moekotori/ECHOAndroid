package app.echo.android.playback

import kotlin.math.sqrt

internal data class EchoSmartTransitionTempo(
    val bpm: Float? = null,
    val confidence: Float = 0f,
    val beatOffsetMs: Int? = null,
    val beatsMs: IntArray = intArrayOf(),
) {
    val reliable: Boolean
        get() = bpm != null && confidence >= EchoSmartTransitionTempoMath.MinimumConfidence
}

internal object EchoSmartTransitionTempoMath {
    const val MinimumConfidence = 0.55f
    private const val HopSeconds = 0.01f

    fun analyze(samples: FloatArray, sampleRate: Int, windowStartMs: Int): EchoSmartTransitionTempo {
        if (sampleRate < 8_000 || samples.size < sampleRate * 2) return EchoSmartTransitionTempo()
        val hop = (sampleRate * HopSeconds).toInt().coerceAtLeast(1)
        val frameCount = (samples.size + hop - 1) / hop
        val onsets = FloatArray(frameCount)
        var previous = 0f
        var meanLevel = 0f
        var onsetSum = 0f
        var peakOnset = 0f
        for (frame in 0 until frameCount) {
            val start = frame * hop
            val end = minOf(samples.size, start + hop)
            var energy = 0.0
            for (index in start until end) {
                val sample = samples[index].toDouble()
                energy += sample * sample
            }
            val level = sqrt(energy / (end - start).coerceAtLeast(1)).toFloat()
            val onset = (level - previous).coerceAtLeast(0f)
            onsets[frame] = onset
            meanLevel += level
            onsetSum += onset
            if (onset > peakOnset) peakOnset = onset
            previous = level
        }
        val mean = meanLevel / frameCount
        if (peakOnset < maxOf(1e-6f, mean * 0.1f)) return EchoSmartTransitionTempo()
        val floor = onsetSum / frameCount * 0.45f
        for (index in onsets.indices) onsets[index] = (onsets[index] - floor).coerceAtLeast(0f)
        val minLag = maxOf(2, Math.round(60f / 200f * sampleRate / hop))
        val maxLag = minOf(onsets.size - 1, Math.round(60f / 60f * sampleRate / hop))
        var bestLag = 0
        var bestScore = 0f
        for (lag in minLag..maxLag) {
            val score = lagCorrelation(onsets, lag)
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestLag == 0 || bestScore < 0.08f) return EchoSmartTransitionTempo()
        var period = bestLag.toFloat()
        var bpm = 60f * sampleRate / (period * hop)
        while (bpm < 80f) {
            bpm *= 2f
            period /= 2f
        }
        while (bpm > 180f) {
            bpm /= 2f
            period *= 2f
        }
        val offsetFrames = measureOffset(onsets, period)
        val offsetMs = if (offsetFrames == null) {
            null
        } else {
            Math.round(offsetFrames * hop * 1000f / sampleRate)
        }
        val beats = beatTimesMs(windowStartMs, samples.size * 1000 / sampleRate, bpm, offsetMs ?: 0)
        return EchoSmartTransitionTempo(
            bpm = (Math.round(bpm * 1000f) / 1000f),
            confidence = bestScore.coerceIn(0f, 1f),
            beatOffsetMs = offsetMs,
            beatsMs = beats,
        )
    }

    fun normalizeBpm(value: Float): Float {
        var bpm = value
        while (bpm < 80f) bpm *= 2f
        while (bpm > 180f) bpm /= 2f
        return bpm
    }

    fun tempoRatio(currentBpm: Float, nextBpm: Float): Float {
        val raw = currentBpm / nextBpm
        return listOf(raw, raw * 2f, raw / 2f).minBy { kotlin.math.abs(it - 1f) }
    }

    fun beatTimesMs(windowStartMs: Int, windowDurationMs: Int, bpm: Float, offsetMs: Int): IntArray {
        if (bpm <= 0f || windowDurationMs <= 0) return intArrayOf()
        val periodMs = (60_000f / bpm).toInt().coerceAtLeast(1)
        val start = windowStartMs + offsetMs.coerceAtLeast(0) % periodMs
        val end = windowStartMs + windowDurationMs
        val beats = ArrayList<Int>((windowDurationMs / periodMs) + 2)
        var time = start
        if (time < windowStartMs) time += periodMs
        while (time <= end) {
            beats += time
            time += periodMs
        }
        return beats.toIntArray()
    }

    fun entryMs(leadingSilenceMs: Int, beatsMs: IntArray): Int {
        val limit = leadingSilenceMs.coerceIn(0, EchoSmartTransitionPolicy.MaxSilenceSkipMs)
        val beat = beatsMs.filter { it in 0..limit }.maxOrNull()
        return (beat ?: limit).coerceIn(0, EchoSmartTransitionPolicy.MaxSilenceSkipMs)
    }

    private fun lagCorrelation(onsets: FloatArray, lag: Int): Float {
        var product = 0.0
        var leftEnergy = 1e-12
        var rightEnergy = 1e-12
        for (index in lag until onsets.size) {
            val left = onsets[index].toDouble()
            val right = onsets[index - lag].toDouble()
            product += left * right
            leftEnergy += left * left
            rightEnergy += right * right
        }
        return (product / sqrt(leftEnergy * rightEnergy)).toFloat()
    }

    private fun measureOffset(onsets: FloatArray, periodFrames: Float): Float? {
        if (periodFrames < 2f) return null
        val bins = minOf(128, maxOf(8, kotlin.math.ceil(periodFrames).toInt()))
        val votes = FloatArray(bins)
        var peaks = 0
        for (index in 1 until onsets.size - 1) {
            if (onsets[index] <= 0f) continue
            if (onsets[index] < onsets[index - 1] || onsets[index] <= onsets[index + 1]) continue
            peaks += 1
            val position = ((index % periodFrames) / periodFrames) * bins
            val bin = position.toInt().coerceIn(0, bins - 1)
            val fraction = position - bin
            votes[bin] += 1f - fraction
            votes[(bin + 1) % bins] += fraction
        }
        if (peaks < 4) return null
        var winner = 0
        var best = 0f
        for (bin in votes.indices) {
            val score = votes[(bin + bins - 1) % bins] + votes[bin] + votes[(bin + 1) % bins]
            if (score > best) {
                best = score
                winner = bin
            }
        }
        if (best / peaks < EchoSmartTransitionTempoMath.MinimumConfidence) return null
        return winner * periodFrames / bins
    }
}
