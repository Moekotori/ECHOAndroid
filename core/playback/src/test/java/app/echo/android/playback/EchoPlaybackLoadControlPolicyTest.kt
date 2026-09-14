package app.echo.android.playback

import app.echo.android.model.settings.EchoEffectivePerformanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoPlaybackLoadControlPolicyTest {
    @Test
    fun lightweightStopsLoadingEarlierThanHighPerformance() {
        val light = EchoPlaybackLoadControlPolicy.forMode(EchoEffectivePerformanceMode.Lightweight)
        val high = EchoPlaybackLoadControlPolicy.forMode(EchoEffectivePerformanceMode.HighPerformance)
        assertTrue(light.maxBufferMs < high.maxBufferMs)
        assertTrue(light.minBufferMs < high.minBufferMs)
        assertTrue(
            EchoPlaybackLoadControlPolicy.shouldContinueLoading(
                bufferedDurationUs = 40_000L * 1_000L,
                maxBufferMs = high.maxBufferMs,
            ),
        )
        assertFalse(
            EchoPlaybackLoadControlPolicy.shouldContinueLoading(
                bufferedDurationUs = 40_000L * 1_000L,
                maxBufferMs = light.maxBufferMs,
            ),
        )
    }

    @Test
    fun playbackCanStartOnceTheConfiguredLeadIsBuffered() {
        val budget = EchoPlaybackLoadControlPolicy.Balanced
        assertFalse(
            EchoPlaybackLoadControlPolicy.shouldStartPlayback(
                bufferedDurationUs = 200_000L,
                playbackSpeed = 1f,
                rebuffering = false,
                budget = budget,
            ),
        )
        assertTrue(
            EchoPlaybackLoadControlPolicy.shouldStartPlayback(
                bufferedDurationUs = 1_000L * 1_000L,
                playbackSpeed = 1f,
                rebuffering = false,
                budget = budget,
            ),
        )
        assertEquals(2_000, budget.bufferForPlaybackAfterRebufferMs)
        assertFalse(
            EchoPlaybackLoadControlPolicy.shouldStartPlayback(
                bufferedDurationUs = 1_000L * 1_000L,
                playbackSpeed = 1f,
                rebuffering = true,
                budget = budget,
            ),
        )
    }

    @Test
    fun usbNetworkEndpointWaitsLongerBeforeStarting() {
        assertFalse(
            EchoPlaybackLoadControlPolicy.isUsbNetworkEndpoint(
                usbExclusive = false,
                usbBitPerfect = false,
                mediaId = "echo-link:a",
                uri = "http://192.168.1.20:26789/echo-link/media/token",
            ),
        )
        assertTrue(
            EchoPlaybackLoadControlPolicy.isUsbNetworkEndpoint(
                usbExclusive = true,
                usbBitPerfect = false,
                mediaId = "echo-link:a",
                uri = "http://192.168.1.20:26789/echo-link/media/token",
            ),
        )
        assertFalse(
            EchoPlaybackLoadControlPolicy.isUsbNetworkEndpoint(
                usbExclusive = true,
                usbBitPerfect = true,
                mediaId = "mediastore:1",
                uri = "content://media/external/audio/media/1",
            ),
        )
        val budget = EchoPlaybackLoadControlPolicy.budgetFor(
            EchoEffectivePerformanceMode.Balanced,
            usbNetworkEndpoint = true,
        )
        assertEquals(30_000, budget.minBufferMs)
        assertEquals(5_000, budget.bufferForPlaybackMs)
        assertEquals(8_000, budget.bufferForPlaybackAfterRebufferMs)
        assertFalse(
            EchoPlaybackLoadControlPolicy.shouldStartPlayback(
                bufferedDurationUs = 1_000L * 1_000L,
                playbackSpeed = 1f,
                rebuffering = false,
                budget = budget,
            ),
        )
        assertTrue(
            EchoPlaybackLoadControlPolicy.shouldStartPlayback(
                bufferedDurationUs = 5_000L * 1_000L,
                playbackSpeed = 1f,
                rebuffering = false,
                budget = budget,
            ),
        )
        val local = EchoPlaybackLoadControlPolicy.budgetFor(
            EchoEffectivePerformanceMode.Balanced,
            usbNetworkEndpoint = false,
        )
        assertEquals(1_000, local.bufferForPlaybackMs)
    }
}
