package app.echo.android.design

import app.echo.android.model.settings.EchoEffectivePerformanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoArtworkMemoryPolicyTest {
    @Test
    fun highPerformanceUsesALargerShareThanLightweight() {
        assertTrue(
            EchoArtworkMemoryPolicy.memoryCachePercent(EchoEffectivePerformanceMode.HighPerformance) >
                EchoArtworkMemoryPolicy.memoryCachePercent(EchoEffectivePerformanceMode.Balanced),
        )
        assertTrue(
            EchoArtworkMemoryPolicy.memoryCachePercent(EchoEffectivePerformanceMode.Balanced) >
                EchoArtworkMemoryPolicy.memoryCachePercent(EchoEffectivePerformanceMode.Lightweight),
        )
    }

    @Test
    fun lowRamDevicesShareOneSmallCap() {
        val percent = EchoArtworkMemoryPolicy.LowRamPercent
        assertEquals(
            percent,
            EchoArtworkMemoryPolicy.memoryCachePercent(EchoEffectivePerformanceMode.HighPerformance, lowRamDevice = true),
            0.0,
        )
        assertEquals(
            percent,
            EchoArtworkMemoryPolicy.memoryCachePercent(EchoEffectivePerformanceMode.Lightweight, lowRamDevice = true),
            0.0,
        )
    }

    @Test
    fun shrinkingTheModeClearsTheMemoryCache() {
        assertTrue(
            EchoArtworkMemoryPolicy.shouldClearMemoryCache(
                EchoEffectivePerformanceMode.HighPerformance,
                EchoEffectivePerformanceMode.Lightweight,
            ),
        )
        assertTrue(
            EchoArtworkMemoryPolicy.shouldClearMemoryCache(
                EchoEffectivePerformanceMode.Balanced,
                EchoEffectivePerformanceMode.Lightweight,
            ),
        )
        assertFalse(
            EchoArtworkMemoryPolicy.shouldClearMemoryCache(
                EchoEffectivePerformanceMode.Lightweight,
                EchoEffectivePerformanceMode.HighPerformance,
            ),
        )
        assertFalse(
            EchoArtworkMemoryPolicy.shouldClearMemoryCache(
                EchoEffectivePerformanceMode.HighPerformance,
                EchoEffectivePerformanceMode.HighPerformance,
            ),
        )
    }
}
