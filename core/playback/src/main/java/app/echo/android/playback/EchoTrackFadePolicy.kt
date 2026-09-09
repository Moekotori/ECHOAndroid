package app.echo.android.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

internal object EchoTrackFadePolicy {
    const val TickMs = 20L

    fun duration(durationMs: Long, requestedMs: Int): Long =
        if (durationMs > 0) minOf(requestedMs.toLong(), max(1, durationMs / 2)) else requestedMs.toLong()

    fun gain(
        positionMs: Long,
        durationMs: Long,
        requestedMs: Int,
        enabled: Boolean,
        suppressFadeOut: Boolean = false,
    ): Float {
        if (!enabled || requestedMs <= 0) return 1f
        val fade = duration(durationMs, requestedMs).coerceAtLeast(1)
        val fadeIn = curve(positionMs.coerceAtLeast(0).toDouble() / fade)
        val fadeOut = if (!suppressFadeOut && durationMs > 0) {
            curve((durationMs - positionMs).coerceAtLeast(0).toDouble() / fade)
        } else {
            1.0
        }
        return (fadeIn * fadeOut).toFloat().coerceIn(0f, 1f)
    }

    /** Null means no remaining fade boundary; an event will reschedule if the duration changes. */
    fun nextDelayMs(positionMs: Long, durationMs: Long, requestedMs: Int, speed: Float): Long? {
        val fade = duration(durationMs, requestedMs)
        if (positionMs < fade || (durationMs > 0 && positionMs >= durationMs - fade)) return TickMs
        if (durationMs <= 0) return null
        return ((durationMs - fade - positionMs) / speed.coerceAtLeast(0.1f)).toLong().coerceAtLeast(TickMs)
    }

    private fun curve(phase: Double): Double = 0.5 - 0.5 * cos(PI * phase.coerceIn(0.0, 1.0))
}
