package app.echo.android.playback

import app.echo.android.model.playback.EchoDspSettings
import app.echo.android.model.playback.EchoVolumeSettings
import java.nio.ByteBuffer
import kotlin.math.*

/** Fixed stereo speaker HRTFs and software-volume equal loudness. Audio-thread owned. */
internal class EchoPersonalAudioKernel {
    private var banks = emptyArray<FloatArray>()
    private var taps = 0
    private var history = FloatArray(0)
    private val cursor = IntArray(1)
    // Wet mix, low/high shelf gains, low L/R, high L/R filter states.
    private val state = FloatArray(7).apply { this[1] = 1f; this[2] = 1f }
    private val params = FloatArray(6)
    private var profile = 1
    private var requestedProfile = 1
    private var requestedMix = 0f
    private var hrtfSupported = false
    private var lastSettings: EchoDspSettings? = null
    private var lastVolume: EchoVolumeSettings? = null
    val neutral: Boolean get() = state[0] == 0f && state[1] == 1f && state[2] == 1f
    val active: Boolean get() = !neutral || requestedMix != 0f || params[1] != 1f || params[2] != 1f

    fun configure(rate: Int) {
        require(rate > 0)
        // Bound direct-convolution cost; high-rate streams keep the inexpensive loudness path.
        hrtfSupported = rate in 8_000..96_000
        taps = if (hrtfSupported) ceil(128.0 * rate / 48_000).toInt() else 1
        banks = Array(3) { bank -> FloatArray(taps * 4) { index ->
            if (!hrtfSupported) return@FloatArray 0f
            val filter = index / taps
            val position = (index % taps) * 48_000.0 / rate
            val first = position.toInt()
            val source = EchoHrtfResponses.banks[bank]
            val a = if (first < 128) source[filter * 128 + first] else 0f
            val b = if (first + 1 < 128) source[filter * 128 + first + 1] else 0f
            ((a + (b - a) * (position - first)) * 48_000 / rate).toFloat()
        } }
        history = FloatArray(taps * 2)
        params[3] = (1 - exp(-1.0 / (rate * 0.02))).toFloat()
        params[4] = (1 - exp(-2 * PI * 100 / rate)).toFloat()
        params[5] = (1 - exp(-2 * PI * min(4500.0, rate * 0.4) / rate)).toFloat()
        lastSettings = null; lastVolume = null
        reset()
    }

    fun setTarget(settings: EchoDspSettings, volume: EchoVolumeSettings) {
        if (lastSettings == settings && lastVolume == volume) return
        lastSettings = settings; lastVolume = volume
        requestedProfile = settings.hrtfProfile
        requestedMix = if (settings.hrtfEnabled && hrtfSupported && !volume.muted) settings.hrtfAmount else 0f
        val fraction = if (settings.equalLoudnessEnabled && !volume.muted)
            ((settings.equalLoudnessReferenceDb - volume.levelDb) / 20f).coerceIn(0f, 1f) else 0f
        params[1] = 10.0.pow(settings.equalLoudnessBassDb * fraction / 20.0).toFloat()
        params[2] = 10.0.pow(settings.equalLoudnessTrebleDb * fraction / 20.0).toFloat()
        if (volume.muted) reset()
    }

    fun reset() {
        history.fill(0f); cursor[0] = 0
        state.fill(0f); state[1] = 1f; state[2] = 1f
        profile = requestedProfile
    }

    fun process(samples: ByteBuffer, frames: Int, channels: Int) {
        if (channels != 2 || taps == 0) { reset(); return }
        if (neutral && requestedMix == 0f && params[1] == 1f && params[2] == 1f) return
        // Fade to dry before changing the response. No allocations or preparation here.
        if (profile != requestedProfile && state[0] == 0f) {
            profile = requestedProfile
            history.fill(0f)
        }
        params[0] = if (profile == requestedProfile) requestedMix else 0f
        if (EchoDspNative.processPersonal(samples, frames, banks[profile], history, cursor, state, params)) {
            clearInactiveHistory()
            return
        }
        val coefficients = banks[profile]
        val start = samples.position()
        for (frame in 0 until frames) {
            val offset = start + frame * 8
            val l = samples.getFloat(offset).let { if (it.isFinite()) it else 0f }
            val r = samples.getFloat(offset + 4).let { if (it.isFinite()) it else 0f }
            val pos = cursor[0]
            history[pos] = l; history[taps + pos] = r
            for (index in 0..2) {
                state[index] += params[3] * (params[index] - state[index])
                if (abs(state[index] - params[index]) < 0.000001f) state[index] = params[index]
            }
            for (ear in 0..1) {
                val dry = if (ear == 0) l else r
                var wet = 0f
                if (state[0] != 0f) {
                    var read = pos
                    for (tap in 0 until taps) {
                        wet += history[read] * coefficients[ear * taps + tap] +
                            history[taps + read] * coefficients[(2 + ear) * taps + tap]
                        if (--read < 0) read = taps - 1
                    }
                }
                val x = dry + state[0] * (wet - dry)
                val lowIndex = 3 + ear
                val highIndex = 5 + ear
                state[lowIndex] += params[4] * (x - state[lowIndex])
                state[highIndex] += params[5] * (x - state[highIndex])
                val y = x + state[lowIndex] * (state[1] - 1) + (x - state[highIndex]) * (state[2] - 1)
                samples.putFloat(offset + ear * 4, if (y.isFinite()) y else 0f)
            }
            cursor[0] = (pos + 1) % taps
        }
        clearInactiveHistory()
    }

    private fun clearInactiveHistory() {
        if (neutral && requestedMix == 0f) {
            history.fill(0f); cursor[0] = 0
            state[3] = 0f; state[4] = 0f; state[5] = 0f; state[6] = 0f
        }
    }
}
