package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.EchoDspSettings
import app.echo.android.model.playback.EchoVolumeSettings
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow

@UnstableApi
class EchoVolumeAudioProcessorTest {
    @Before @After fun clear() {
        EchoPlaybackProcessRuntime.setVolumeSettings(EchoVolumeSettings())
        EchoPlaybackProcessRuntime.setDspSettings(EchoDspSettings())
        EchoPlaybackProcessRuntime.dspReplayGainDb = 0f
    }

    @Test fun volumeAndMuteChangeSmoothlyWithoutReconfiguringTheStream() {
        val processor = processor(2)
        assertEquals(0.1f, feed(processor, 1, 2).last(), 0f)
        EchoPlaybackProcessRuntime.setVolumeSettings(EchoVolumeSettings(gainDb = 6f))
        val raised = feed(processor, 16000, 2)
        assertTrue(raised.first() > 0.1f && raised.first() < 0.101f)
        assertEquals(0.1f * 10.0.pow(6.0 / 20).toFloat(), raised.last(), 0.00001f)
        assertTrue(raised.zipWithNext().all { abs(it.second - it.first) < 0.001f })
        EchoPlaybackProcessRuntime.setVolumeSettings(EchoVolumeSettings(gainDb = 6f, muted = true))
        val muted = feed(processor, 16000, 2)
        assertTrue(muted.first() > 0.19f)
        assertEquals(0f, muted.last(), 0f)
        EchoPlaybackProcessRuntime.setVolumeSettings(EchoVolumeSettings(gainDb = 6f))
        val resumed = feed(processor, 16000, 2)
        assertTrue(resumed.first() in 0f..0.001f)
        assertEquals(raised.last(), resumed.last(), 0.00001f)
        processor.reset()
    }

    @Test fun combinedCeilingAndLinkedPeakLimiterApplyToMonoStereoAndSurround() {
        for (channels in listOf(1, 2, 6)) {
            EchoPlaybackProcessRuntime.setVolumeSettings(EchoVolumeSettings(levelDb = -3f, gainDb = 12f, limitDb = 3f))
            EchoPlaybackProcessRuntime.dspReplayGainDb = 6f
            var processor = processor(channels)
            assertEquals(0.1f * 10.0.pow(3.0 / 20).toFloat(), feed(processor, 8, channels).last(), 0.00001f)
            processor.reset()
            EchoPlaybackProcessRuntime.setDspSettings(EchoDspSettings(limiterEnabled = true))
            processor = processor(channels)
            val limited = feed(processor, 8, channels, 0.9f)
            assertTrue(limited.all { it.isFinite() && abs(it) <= 0.891252f })
            processor.reset()
            EchoPlaybackProcessRuntime.setDspSettings(EchoDspSettings())
        }
    }

    private fun processor(channels: Int) = EchoDspAudioProcessor(emptyArray()).also {
        it.configure(AudioProcessor.AudioFormat(48000, channels, C.ENCODING_PCM_FLOAT))
        it.flush(AudioProcessor.StreamMetadata.DEFAULT)
    }

    private fun feed(processor: EchoDspAudioProcessor, frames: Int, channels: Int, value: Float = 0.1f): List<Float> {
        val input = ByteBuffer.allocateDirect(frames * channels * 4).order(ByteOrder.nativeOrder())
        repeat(frames * channels) { input.putFloat(value) }
        input.flip()
        val result = ArrayList<Float>()
        while (input.hasRemaining()) {
            processor.queueInput(input)
            val output = processor.output.order(ByteOrder.nativeOrder())
            while (output.hasRemaining()) result.add(output.float)
        }
        return result
    }
}
