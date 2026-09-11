package app.echo.android.playback

import app.echo.android.model.settings.EchoEffectivePerformanceMode

internal data class EchoPlaybackBufferBudget(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
)

internal object EchoPlaybackLoadControlPolicy {
    val Lightweight = EchoPlaybackBufferBudget(
        minBufferMs = 15_000,
        maxBufferMs = 25_000,
        bufferForPlaybackMs = 1_000,
        bufferForPlaybackAfterRebufferMs = 2_000,
    )
    val Balanced = EchoPlaybackBufferBudget(
        minBufferMs = 30_000,
        maxBufferMs = 50_000,
        bufferForPlaybackMs = 1_000,
        bufferForPlaybackAfterRebufferMs = 2_000,
    )
    val HighPerformance = EchoPlaybackBufferBudget(
        minBufferMs = 50_000,
        maxBufferMs = 80_000,
        bufferForPlaybackMs = 1_000,
        bufferForPlaybackAfterRebufferMs = 2_500,
    )

    fun forMode(mode: EchoEffectivePerformanceMode): EchoPlaybackBufferBudget = when {
        mode.isLightweight -> Lightweight
        mode.isHighPerformance -> HighPerformance
        else -> Balanced
    }

    fun shouldContinueLoading(bufferedDurationUs: Long, maxBufferMs: Int): Boolean {
        if (maxBufferMs <= 0) return false
        return bufferedDurationUs < maxBufferMs * 1_000L
    }

    fun shouldStartPlayback(
        bufferedDurationUs: Long,
        playbackSpeed: Float,
        rebuffering: Boolean,
        budget: EchoPlaybackBufferBudget,
    ): Boolean {
        val playoutUs = if (playbackSpeed > 0f) {
            (bufferedDurationUs / playbackSpeed.toDouble()).toLong()
        } else {
            bufferedDurationUs
        }
        val neededMs = if (rebuffering) {
            budget.bufferForPlaybackAfterRebufferMs
        } else {
            budget.bufferForPlaybackMs
        }
        return playoutUs >= neededMs * 1_000L
    }
}
