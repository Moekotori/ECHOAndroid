package app.echo.android.design

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import app.echo.android.model.settings.EchoEffectivePerformanceMode

object EchoArtworkMemoryPolicy {
    const val LightweightPercent = 0.10
    const val BalancedPercent = 0.18
    const val HighPerformancePercent = 0.25
    const val LowRamPercent = 0.10

    fun memoryCachePercent(
        mode: EchoEffectivePerformanceMode,
        lowRamDevice: Boolean = false,
    ): Double {
        if (lowRamDevice) return LowRamPercent
        return when {
            mode.isLightweight -> LightweightPercent
            mode.isHighPerformance -> HighPerformancePercent
            else -> BalancedPercent
        }
    }

    fun shouldClearMemoryCache(
        previous: EchoEffectivePerformanceMode,
        next: EchoEffectivePerformanceMode,
        lowRamDevice: Boolean = false,
    ): Boolean = memoryCachePercent(next, lowRamDevice) < memoryCachePercent(previous, lowRamDevice)

    fun sizeBytes(context: Context, mode: EchoEffectivePerformanceMode): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowRam = activityManager?.isLowRamDevice == true
        val memoryClassMb = try {
            val largeHeap = context.applicationInfo.flags and ApplicationInfo.FLAG_LARGE_HEAP != 0
            when {
                activityManager == null -> DefaultMemoryClassMb
                largeHeap -> activityManager.largeMemoryClass
                else -> activityManager.memoryClass
            }
        } catch (_: RuntimeException) {
            DefaultMemoryClassMb
        }
        val percent = memoryCachePercent(mode, lowRam)
        return (percent * memoryClassMb * 1024.0 * 1024.0).toInt().coerceAtLeast(1)
    }

    private const val DefaultMemoryClassMb = 256
}
