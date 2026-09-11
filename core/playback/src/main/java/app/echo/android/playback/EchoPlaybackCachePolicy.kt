package app.echo.android.playback

import android.app.ActivityManager
import android.content.Context
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object EchoPlaybackCachePolicy {
    const val BalancedMaxBytes = 256L * 1024L * 1024L
    const val LightweightMaxBytes = 128L * 1024L * 1024L
    const val HighPerformanceMaxBytes = 512L * 1024L * 1024L
    const val LowRamMaxBytes = 64L * 1024L * 1024L
    const val MinimumMaxBytes = 16L * 1024L * 1024L

    private val maxBytes = AtomicLong(BalancedMaxBytes)
    private val mode = AtomicReference(EchoEffectivePerformanceMode.Balanced)
    private val usableSpaceBytes = AtomicLong(Long.MAX_VALUE)
    private val lowRamDevice = AtomicBoolean(false)

    val maxCacheBytes: Long
        get() = maxBytes.get()

    val effectiveMode: EchoEffectivePerformanceMode
        get() = mode.get()

    fun bindDeviceConstraints(context: Context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        bindDeviceConstraints(
            usableSpaceBytes = context.cacheDir.usableSpace,
            lowRamDevice = activityManager?.isLowRamDevice == true,
        )
    }

    fun bindDeviceConstraints(usableSpaceBytes: Long, lowRamDevice: Boolean) {
        this.usableSpaceBytes.set(if (usableSpaceBytes < 0L) 0L else usableSpaceBytes)
        this.lowRamDevice.set(lowRamDevice)
        applyMaxBytes()
    }

    fun setEffectivePerformanceMode(mode: EchoEffectivePerformanceMode) {
        this.mode.set(mode)
        applyMaxBytes()
    }

    fun resolveMaxBytes(
        mode: EchoEffectivePerformanceMode,
        usableSpaceBytes: Long = Long.MAX_VALUE,
        lowRamDevice: Boolean = false,
    ): Long {
        val nominal = when {
            mode.isLightweight -> LightweightMaxBytes
            mode.isHighPerformance -> HighPerformanceMaxBytes
            else -> BalancedMaxBytes
        }
        val ramLimited = if (lowRamDevice) minOf(nominal, LowRamMaxBytes) else nominal
        if (usableSpaceBytes == Long.MAX_VALUE) return ramLimited
        val free = usableSpaceBytes.coerceAtLeast(0L)
        val diskBudget = (free / 4L).coerceAtMost(free)
        if (diskBudget <= 0L) return minOf(ramLimited, MinimumMaxBytes)
        return minOf(ramLimited, diskBudget.coerceAtLeast(MinimumMaxBytes).coerceAtMost(free))
    }

    private fun applyMaxBytes() {
        val next = resolveMaxBytes(mode.get(), usableSpaceBytes.get(), lowRamDevice.get())
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
