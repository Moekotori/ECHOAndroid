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
}
