package app.echo.android.playback

import app.echo.android.model.settings.EchoEffectivePerformanceMode
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object EchoPlaybackCachePolicy {
    const val BalancedMaxBytes = 256L * 1024L * 1024L
    const val LightweightMaxBytes = 128L * 1024L * 1024L
    const val HighPerformanceMaxBytes = 512L * 1024L * 1024L

    private val maxBytes = AtomicLong(BalancedMaxBytes)
    private val mode = AtomicReference(EchoEffectivePerformanceMode.Balanced)

    val maxCacheBytes: Long
        get() = maxBytes.get()

    val effectiveMode: EchoEffectivePerformanceMode
        get() = mode.get()

    fun setEffectivePerformanceMode(mode: EchoEffectivePerformanceMode) {
        this.mode.set(mode)
        val next = when {
            mode.isLightweight -> LightweightMaxBytes
            mode.isHighPerformance -> HighPerformanceMaxBytes
            else -> BalancedMaxBytes
        }
        val previous = maxBytes.getAndSet(next)
        if (next < previous) {
            EchoPlaybackCacheTrim.action()
        }
    }
}

internal object EchoPlaybackCacheTrim {
    @Volatile
    var action: () -> Unit = {}
}
