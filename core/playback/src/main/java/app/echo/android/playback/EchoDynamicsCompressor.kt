package app.echo.android.playback

import app.echo.android.model.playback.EchoCompressorMode
import kotlin.math.exp
import kotlin.math.sqrt

/** Linked peak detector; fixed state, no lookahead allocation, logs or I/O per frame. */
internal class EchoDynamicsCompressor {
    private var mode = EchoCompressorMode.Off
    private var envelope = 0f
    private var smoothedGain = 1f
    private var attack = 0f
    private var release = 0f
    private var gainSmoothing = 0f
    private var threshold = 1f
    private var makeup = 1f
    val enabled get() = mode != EchoCompressorMode.Off

    fun configure(sampleRate: Int) {
        val rate = sampleRate.coerceAtLeast(8000)
        attack = (1 - exp(-1.0 / (rate * 0.005))).toFloat()
        release = (1 - exp(-1.0 / (rate * 0.180))).toFloat()
        gainSmoothing = (1 - exp(-1.0 / (rate * 0.010))).toFloat()
        reset()
    }

    fun setMode(value: EchoCompressorMode) {
        mode = value
        threshold = when (value) { EchoCompressorMode.Night -> 0.12589254f; else -> 0.25118864f }
        makeup = when (value) { EchoCompressorMode.Gentle -> 1.2589254f; EchoCompressorMode.Night -> 1.5848932f; else -> 1f }
    }

    fun reset() { envelope = 0f; smoothedGain = 1f }

    fun gain(peak: Float): Float {
        val target = if (!enabled) 1f else {
            val level = peak.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
            envelope += (if (level > envelope) attack else release) * (level - envelope)
            val ratio = if (envelope > threshold) (threshold / envelope).coerceIn(0f, 1f) else 1f
            // 2:1 and 4:1 compression, without a logarithm or pow in the hot path.
            val attenuation = if (mode == EchoCompressorMode.Night) sqrt(ratio) * sqrt(sqrt(ratio)) else sqrt(ratio)
            attenuation * makeup
        }
        smoothedGain += gainSmoothing * (target - smoothedGain)
        // Float increments eventually round to zero; explicitly reach unity so Off can bypass.
        if (kotlin.math.abs(smoothedGain - target) < 0.0001f) smoothedGain = target
        return smoothedGain
    }

    val neutral get() = !enabled && smoothedGain == 1f
}
