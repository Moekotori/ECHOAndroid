package app.echo.android.model.lyrics

import app.echo.android.model.playback.EchoOutputDeviceKind

/** Output latency changes the display clock, never the saved lyric timestamps or seek targets. */
object EchoLyricsOutputTiming {
    fun effectiveDelayMs(delayMs: Int, outputKind: String): Int =
        if (outputKind == EchoOutputDeviceKind.Bluetooth.id) delayMs.coerceIn(0, 2_000) else 0

    fun mediaDelayMs(delayMs: Int, speed: Float): Long {
        val rate = speed.takeIf { it.isFinite() && it > 0f } ?: 1f
        return (delayMs.coerceIn(0, 2_000) * rate.toDouble()).toLong()
    }

    // Negative positions keep a lyric at 00:00 from appearing before its compensated start.
    fun positionMs(playbackPositionMs: Long, delayMs: Int, speed: Float): Long =
        playbackPositionMs.coerceAtLeast(0L) - mediaDelayMs(delayMs, speed)
}
