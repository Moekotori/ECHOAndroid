package app.echo.android.playback

import app.echo.android.model.playback.EchoLinkPlaybackUri
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
    const val UsbNetworkStartMs = 5_000
    const val UsbNetworkRebufferMs = 8_000

    fun forMode(mode: EchoEffectivePerformanceMode): EchoPlaybackBufferBudget = when {
        mode.isLightweight -> Lightweight
        mode.isHighPerformance -> HighPerformance
        else -> Balanced
    }

    fun isUsbNetworkEndpoint(
        usbExclusive: Boolean,
        usbBitPerfect: Boolean,
        mediaId: String?,
        uri: String,
    ): Boolean {
        if (!usbExclusive && !usbBitPerfect) return false
        if (EchoLinkPlaybackUri.isOneShotStreamUri(uri)) return true
        val id = mediaId?.takeIf { it.isNotBlank() } ?: return false
        return EchoLinkPlaybackUri.requiresStreamResolve(id, uri)
    }

    fun forUsbNetworkEndpoint(base: EchoPlaybackBufferBudget): EchoPlaybackBufferBudget =
        base.copy(
            bufferForPlaybackMs = maxOf(base.bufferForPlaybackMs, UsbNetworkStartMs),
            bufferForPlaybackAfterRebufferMs = maxOf(
                base.bufferForPlaybackAfterRebufferMs,
                UsbNetworkRebufferMs,
            ),
        )

    fun budgetFor(
        mode: EchoEffectivePerformanceMode,
        usbNetworkEndpoint: Boolean,
    ): EchoPlaybackBufferBudget {
        val base = forMode(mode)
        return if (usbNetworkEndpoint) forUsbNetworkEndpoint(base) else base
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
