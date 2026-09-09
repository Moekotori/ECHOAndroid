package app.echo.android.playback

import java.nio.ShortBuffer

internal data class EchoSmartTransitionAnalysis(
    val durationMs: Long,
    val leadingSilenceMs: Int,
    val trailingSilenceMs: Int,
    val headEnergy: Float,
    val tailEnergy: Float,
    val sampleRateHz: Int? = null,
    val hasIntro: Boolean = true,
    val hasOutro: Boolean = true,
    val bpm: Float? = null,
    val bpmConfidence: Float = 0f,
    val beatOffsetMs: Int? = null,
    val beatsMs: IntArray = intArrayOf(),
    val introVocal: EchoSmartTransitionVocal = EchoSmartTransitionVocal(),
    val outroVocal: EchoSmartTransitionVocal = EchoSmartTransitionVocal(),
) {
    fun merge(other: EchoSmartTransitionAnalysis): EchoSmartTransitionAnalysis =
        EchoSmartTransitionAnalysis(
            durationMs = durationMs.takeIf { it > 0 } ?: other.durationMs,
            leadingSilenceMs = if (hasIntro) leadingSilenceMs else other.leadingSilenceMs,
            trailingSilenceMs = if (hasOutro) trailingSilenceMs else other.trailingSilenceMs,
            headEnergy = if (hasIntro) headEnergy else other.headEnergy,
            tailEnergy = if (hasOutro) tailEnergy else other.tailEnergy,
            sampleRateHz = sampleRateHz ?: other.sampleRateHz,
            hasIntro = hasIntro || other.hasIntro,
            hasOutro = hasOutro || other.hasOutro,
            bpm = if (hasIntro) bpm else other.bpm,
            bpmConfidence = if (hasIntro) bpmConfidence else other.bpmConfidence,
            beatOffsetMs = if (hasIntro) beatOffsetMs else other.beatOffsetMs,
            beatsMs = if (hasIntro && beatsMs.isNotEmpty()) beatsMs else other.beatsMs,
            introVocal = if (hasIntro) introVocal else other.introVocal,
            outroVocal = if (hasOutro) outroVocal else other.outroVocal,
        )

    fun covers(needIntro: Boolean, needOutro: Boolean): Boolean =
        (!needIntro || hasIntro) && (!needOutro || hasOutro)
}

internal object EchoSmartTransitionAnalysisMath {
    fun fromMono(
        samples: FloatArray,
        sampleRate: Int,
        durationMs: Long,
        nativeSampleRateHz: Int? = null,
        hasIntro: Boolean = true,
        hasOutro: Boolean = true,
        vocal: EchoSmartTransitionVocal = EchoSmartTransitionVocal(),
    ): EchoSmartTransitionAnalysis {
        if (samples.isEmpty() || sampleRate <= 0) {
            return EchoSmartTransitionAnalysis(
                durationMs = durationMs,
                leadingSilenceMs = 0,
                trailingSilenceMs = 0,
                headEnergy = 0f,
                tailEnergy = 0f,
                sampleRateHz = nativeSampleRateHz,
                hasIntro = hasIntro,
                hasOutro = hasOutro,
                introVocal = if (hasIntro) vocal else EchoSmartTransitionVocal(),
                outroVocal = if (hasOutro) vocal else EchoSmartTransitionVocal(),
            )
        }
        val frameSize = (sampleRate * EchoSmartTransitionPolicy.FrameMs / 1_000).coerceAtLeast(1)
        val frameCount = (samples.size / frameSize).coerceAtLeast(1)
        val energies = FloatArray(frameCount)
        for (frame in 0 until frameCount) {
            val start = frame * frameSize
            val end = minOf(samples.size, start + frameSize)
            energies[frame] = rmsDb(samples, start, end)
        }
        val leading = countEdgeSilence(energies, fromStart = true) * EchoSmartTransitionPolicy.FrameMs
        val trailing = countEdgeSilence(energies, fromStart = false) * EchoSmartTransitionPolicy.FrameMs
        val headCount = (frameCount / 4).coerceAtLeast(1)
        val headEnergy = meanEnergy01(energies, 0, headCount)
        val tailStart = (frameCount - headCount).coerceAtLeast(0)
        val tailEnergy = meanEnergy01(energies, tailStart, frameCount)
        val windowMs = (samples.size * 1_000L / sampleRate).toInt()
        val tempo = EchoSmartTransitionTempoMath.analyze(samples, sampleRate, windowStartMs = 0)
        return EchoSmartTransitionAnalysis(
            durationMs = durationMs,
            leadingSilenceMs = leading.coerceAtMost(windowMs),
            trailingSilenceMs = trailing.coerceAtMost(windowMs),
            headEnergy = headEnergy,
            tailEnergy = tailEnergy,
            sampleRateHz = nativeSampleRateHz,
            hasIntro = hasIntro,
            hasOutro = hasOutro,
            bpm = tempo.bpm,
            bpmConfidence = tempo.confidence,
            beatOffsetMs = tempo.beatOffsetMs,
            beatsMs = tempo.beatsMs,
            introVocal = if (hasIntro) vocal else EchoSmartTransitionVocal(),
            outroVocal = if (hasOutro) vocal else EchoSmartTransitionVocal(),
        )
    }

    fun downsampleToMono(input: FloatArray, inputRate: Int, channels: Int, targetRate: Int): FloatArray {
        if (input.isEmpty() || inputRate <= 0 || targetRate <= 0 || channels <= 0) return FloatArray(0)
        val frames = input.size / channels
        val maxOut = ((frames.toLong() * targetRate) / inputRate).toInt() + 2
        val writer = EchoSmartTransitionDownsampler(inputRate, channels, targetRate, maxOut)
        writer.pushInterleaved(input, frames)
        return writer.toArray()
    }

    private fun countEdgeSilence(energies: FloatArray, fromStart: Boolean): Int {
        var count = 0
        val indices = if (fromStart) energies.indices else energies.indices.reversed()
        for (index in indices) {
            if (energies[index] > EchoSmartTransitionPolicy.SilenceThresholdDb) break
            count += 1
        }
        return count
    }

    private fun meanEnergy01(values: FloatArray, start: Int, end: Int): Float {
        val from = start.coerceIn(0, values.size)
        val to = end.coerceIn(from, values.size)
        if (to <= from) return 0f
        var sum = 0f
        for (index in from until to) sum += energy01(values[index])
        return sum / (to - from)
    }

    private fun energy01(rmsDb: Float): Float =
        ((rmsDb - EchoSmartTransitionPolicy.SilenceThresholdDb) / 42f).coerceIn(0f, 1f)

    private fun rmsDb(samples: FloatArray, start: Int, end: Int): Float {
        if (end <= start) return -96f
        var sum = 0.0
        for (index in start until end) {
            val sample = samples[index].toDouble()
            sum += sample * sample
        }
        val rms = kotlin.math.sqrt(sum / (end - start))
        if (rms <= 1e-9) return -96f
        return (20.0 * kotlin.math.log10(rms)).toFloat()
    }
}

internal class EchoSmartTransitionDownsampler(
    private val inputRate: Int,
    private val channels: Int,
    private val targetRate: Int,
    maxSamples: Int,
) {
    private val output = FloatArray(maxSamples.coerceAtLeast(0))
    private var size = 0
    private var phase = 0
    private val channelCount = channels.coerceAtLeast(1)

    fun pushInterleaved(pcm: FloatArray, frames: Int) {
        if (inputRate <= 0 || targetRate <= 0 || frames <= 0) return
        val safeFrames = minOf(frames, pcm.size / channelCount)
        for (frame in 0 until safeFrames) {
            var mono = 0f
            val base = frame * channelCount
            for (channel in 0 until channelCount) {
                mono += pcm[base + channel]
            }
            acceptMono(mono / channelCount)
        }
    }

    fun pushStereoFrame(left: Float, right: Float = left) {
        val mono = if (channelCount <= 1) left else (left + right) * 0.5f
        acceptMono(mono)
    }

    fun pushPcm16(buffer: ShortBuffer, frames: Int) {
        if (inputRate <= 0 || targetRate <= 0 || frames <= 0) return
        repeat(frames) {
            if (buffer.remaining() < channelCount) return
            var mono = 0f
            repeat(channelCount) {
                mono += buffer.get() / 32768f
            }
            acceptMono(mono / channelCount)
        }
    }

    fun toArray(): FloatArray = output.copyOf(size)

    private fun acceptMono(mono: Float) {
        phase += targetRate
        if (phase >= inputRate && size < output.size) {
            phase -= inputRate
            output[size++] = mono
        }
    }
}
