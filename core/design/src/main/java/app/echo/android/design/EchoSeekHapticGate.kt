package app.echo.android.design

/** Twenty detents with a time bound, even when the pointer jumps across many of them. */
internal class EchoSeekHapticGate {
    private var bucket: Int? = null
    private var lastPulseMs: Long? = null

    fun shouldPulse(fraction: Float, nowMs: Long): Boolean {
        if (!fraction.isFinite()) return false
        return shouldPulseStep((fraction.coerceIn(0f, 1f) * 20).toInt(), nowMs)
    }

    fun shouldPulseStep(next: Int, nowMs: Long): Boolean {
        val previous = bucket
        bucket = next
        if (previous == null || previous == next) return false
        if (lastPulseMs?.let { nowMs >= it && nowMs - it < 65L } == true) return false
        lastPulseMs = nowMs
        return true
    }

    fun reset() { bucket = null; lastPulseMs = null }
}
