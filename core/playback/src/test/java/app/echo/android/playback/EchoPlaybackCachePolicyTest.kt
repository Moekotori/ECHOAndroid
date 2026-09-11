package app.echo.android.playback

import app.echo.android.model.settings.EchoEffectivePerformanceMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EchoPlaybackCachePolicyTest {
    @Before
    fun resetPolicy() {
        EchoPlaybackCacheTrim.action = {}
        EchoPlaybackCachePolicy.bindDeviceConstraints(Long.MAX_VALUE, false)
        EchoPlaybackCachePolicy.setEffectivePerformanceMode(EchoEffectivePerformanceMode.Balanced)
    }

    @After
    fun restorePolicy() {
        EchoPlaybackCacheTrim.action = {}
        EchoPlaybackCachePolicy.bindDeviceConstraints(Long.MAX_VALUE, false)
        EchoPlaybackCachePolicy.setEffectivePerformanceMode(EchoEffectivePerformanceMode.Balanced)
    }

    @Test
    fun highPerformanceRaisesTheCap() {
        EchoPlaybackCachePolicy.setEffectivePerformanceMode(EchoEffectivePerformanceMode.HighPerformance)
        assertEquals(EchoPlaybackCachePolicy.HighPerformanceMaxBytes, EchoPlaybackCachePolicy.maxCacheBytes)
    }

    @Test
    fun shrinkingTheCapInvokesTrim() {
        var trimmed = false
        EchoPlaybackCacheTrim.action = { trimmed = true }
        EchoPlaybackCachePolicy.setEffectivePerformanceMode(EchoEffectivePerformanceMode.HighPerformance)
        EchoPlaybackCachePolicy.setEffectivePerformanceMode(EchoEffectivePerformanceMode.Lightweight)
        assertTrue(trimmed)
        assertEquals(EchoPlaybackCachePolicy.LightweightMaxBytes, EchoPlaybackCachePolicy.maxCacheBytes)
    }

    @Test
    fun raisingTheCapDoesNotTrim() {
        EchoPlaybackCachePolicy.setEffectivePerformanceMode(EchoEffectivePerformanceMode.Lightweight)
        var trimmed = false
        EchoPlaybackCacheTrim.action = { trimmed = true }
        EchoPlaybackCachePolicy.setEffectivePerformanceMode(EchoEffectivePerformanceMode.HighPerformance)
        assertEquals(false, trimmed)
    }

    @Test
    fun lowRamDevicesCapBelowTheNominalHighPerformanceBudget() {
        assertEquals(
            EchoPlaybackCachePolicy.LowRamMaxBytes,
            EchoPlaybackCachePolicy.resolveMaxBytes(
                mode = EchoEffectivePerformanceMode.HighPerformance,
                lowRamDevice = true,
            ),
        )
        assertTrue(
            EchoPlaybackCachePolicy.resolveMaxBytes(EchoEffectivePerformanceMode.Lightweight, lowRamDevice = true) <=
                EchoPlaybackCachePolicy.LowRamMaxBytes,
        )
    }

    @Test
    fun freeSpaceCapsTheCacheBelowAQuarterOfWhatIsUsable() {
        val usable = 200L * 1024L * 1024L
        val resolved = EchoPlaybackCachePolicy.resolveMaxBytes(
            mode = EchoEffectivePerformanceMode.HighPerformance,
            usableSpaceBytes = usable,
        )
        assertEquals(usable / 4L, resolved)
        assertTrue(resolved < EchoPlaybackCachePolicy.HighPerformanceMaxBytes)
    }

    @Test
    fun bindingASmallerDeviceBudgetTrims() {
        var trimmed = false
        EchoPlaybackCacheTrim.action = { trimmed = true }
        EchoPlaybackCachePolicy.setEffectivePerformanceMode(EchoEffectivePerformanceMode.HighPerformance)
        EchoPlaybackCachePolicy.bindDeviceConstraints(usableSpaceBytes = 64L * 1024L * 1024L, lowRamDevice = true)
        assertTrue(trimmed)
        assertTrue(EchoPlaybackCachePolicy.maxCacheBytes <= EchoPlaybackCachePolicy.LowRamMaxBytes)
    }
}
