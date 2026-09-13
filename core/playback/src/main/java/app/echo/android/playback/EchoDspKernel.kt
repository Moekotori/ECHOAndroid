package app.echo.android.playback

import app.echo.android.model.playback.EchoDspSettings
import kotlin.math.*

/** Stereo crossfeed followed by a linked sample-peak limiter. No allocations in process(). */
internal class EchoDspKernel {
    var left = 0f
        private set
    var right = 0f
        private set
    private var lowLeft = 0f
    private var lowRight = 0f
    private var delayedLeft = FloatArray(1)
    private var delayedRight = FloatArray(1)
    private var cursor = 0
    private var lowAlpha = 0f
    private var smooth = 0f
    private var release = 0f
    private var crossMix = 0f
    private var level = 1f
    private var reduction = 1f
    private var ceiling = 1f
    private var targetMix = 0f
    private var targetLevel = 1f
    private var limiting = false

    val neutral: Boolean get() = crossMix == 0f && level == 1f && reduction == 1f

    fun configure(rate: Int) {
        lowAlpha = (1 - exp(-2 * PI * 700 / rate)).toFloat()
        smooth = (1 - exp(-1.0 / (rate * 0.02))).toFloat()
        release = (1 - exp(-1.0 / (rate * 0.08))).toFloat()
        delayedLeft = FloatArray((rate * 0.00025).roundToInt().coerceAtLeast(1))
        delayedRight = FloatArray(delayedLeft.size)
        reset()
    }

    fun setTarget(settings: EchoDspSettings, replayGainDb: Float) {
        targetMix = if (settings.crossfeedEnabled) settings.crossfeedAmount else 0f
        targetLevel = 10.0.pow(replayGainDb.coerceIn(-36f, 30f) / 20.0).toFloat()
        limiting = settings.limiterEnabled
        ceiling = 10.0.pow(settings.limiterCeilingDb / 20.0).toFloat()
    }

    fun reset() {
        lowLeft = 0f; lowRight = 0f; cursor = 0
        delayedLeft.fill(0f); delayedRight.fill(0f)
        crossMix = targetMix; level = targetLevel; reduction = 1f
    }

    fun process(l: Float, r: Float, stereo: Boolean) {
        crossMix += smooth * (targetMix - crossMix)
        level += smooth * (targetLevel - level)
        if (abs(crossMix - targetMix) < 0.000001f) crossMix = targetMix
        if (abs(level - targetLevel) < 0.000001f) level = targetLevel
        lowLeft += lowAlpha * (l - lowLeft)
        lowRight += lowAlpha * (r - lowRight)
        val dl = delayedLeft[cursor]
        val dr = delayedRight[cursor]
        delayedLeft[cursor] = lowLeft
        delayedRight[cursor] = lowRight
        cursor = (cursor + 1) % delayedLeft.size
        // Normalize low-frequency gain. Mono is never crossfed.
        val mix = if (stereo) crossMix else 0f
        val a = ((l + dr * mix) / (1 + mix)) * level
        val b = ((r + dl * mix) / (1 + mix)) * level
        val peak = max(abs(a), if (stereo) abs(b) else 0f)
        val wanted = if (limiting && peak > ceiling) ceiling / peak else 1f
        reduction = if (wanted < reduction) wanted else reduction + release * (wanted - reduction)
        if (abs(reduction - 1f) < 0.000001f) reduction = 1f
        left = a * reduction
        right = b * reduction
    }
}
