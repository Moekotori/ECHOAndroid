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
        EchoPlaybackCachePolicy.setEffectivePerformanceMode(EchoEffectivePerformanceMode.Balanced)
    }

    @After
    fun restorePolicy() {
        EchoPlaybackCacheTrim.action = {}
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
}
