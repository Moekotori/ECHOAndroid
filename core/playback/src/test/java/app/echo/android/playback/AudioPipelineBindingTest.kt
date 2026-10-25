package app.echo.android.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import app.echo.android.model.playback.*
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class AudioPipelineBindingTest {
    private class InitializationFailure : PlaybackException("initialization", null,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, android.os.Bundle.EMPTY, 0L)
    private val event = AnalyticsListener.EventTime(0, Timeline.EMPTY, 0, null, 0, Timeline.EMPTY, 0, null, 0, 0)
    @Test fun runtimeEventsReportRecoveryWithoutInventingFallbackAndClearOnDisable() {
        var registered: Any? = null
        val player = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { proxy, method, args ->
            when (method.name) {
                "addAnalyticsListener" -> { registered = args!![0]; null }
                "removeAnalyticsListener" -> { registered = null; null }
                "getAudioSessionId" -> 42
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> null
            }
        } as ExoPlayer
        val pipeline = EchoAudioPipelineState()
        val binding = EchoAudioPipelineBinding(player, pipeline)
        try {
            assertSame(binding, registered)
            binding.onAudioEnabled(event, DecoderCounters())
            binding.onPlayerError(event, InitializationFailure())
            binding.onAudioDecoderInitialized(event, "c2.android.aac.decoder", 0, 23)
            val readout = pipeline.diagnostics.value.decoder
            assertEquals("c2.android.aac.decoder", readout.name)
            assertEquals(EchoDecoderImplementation.Platform, readout.implementation)
            assertEquals(23L, readout.initializationDurationMs)
            assertEquals(1, readout.initializationCount)
            assertEquals(1, readout.initializationFailureCount)
            assertTrue(readout.initializationRecoveryObserved)
            binding.onAudioDisabled(event, DecoderCounters())
            assertFalse(pipeline.diagnostics.value.decoder.audioEnabled)
            assertNull(pipeline.diagnostics.value.decoder.name)
            assertNull(pipeline.diagnostics.value.decoder.audioSessionId)
        } finally { binding.close() }
        assertNull(registered)
        assertEquals(EchoDecoderDiagnostics(), pipeline.diagnostics.value.decoder)
    }
}
