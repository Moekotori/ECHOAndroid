package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

@UnstableApi
class EchoDspAudioProcessorTest {
    @After fun clear() { EchoPlaybackProcessRuntime.setDspSettings(EchoDspSettings()); EchoPlaybackProcessRuntime.dspReplayGainDb = 0f }

    @Test fun limiterBoundsAllPresetsWithAdditionalReplayGainWithoutEarlyClipping() {
        for (preset in EchoEqualizerPresets.presets) {
            EchoPlaybackProcessRuntime.setDspSettings(EchoDspSettings(limiterEnabled = true))
            EchoPlaybackProcessRuntime.dspReplayGainDb = 6f
            val eq = EchoEqualizerAudioProcessor()
            eq.setRuntime(EchoEqualizerRuntime(true, 0f, EchoEqualizerEngine.graphicFilters(preset.gainsDb)))
            val processor = EchoDspAudioProcessor(arrayOf(eq))
            processor.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT))
            processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
            assertTrue(eq.preserveFloatHeadroom)
            val input = ByteBuffer.allocateDirect(48000 * 8).order(ByteOrder.nativeOrder())
            repeat(48000) { val x = (0.98 * sin(2 * PI * 60 * it / 48000)).toFloat(); input.putFloat(x); input.putFloat(x * 0.5f) }
            input.flip()
            var frames = 0
            while (input.hasRemaining()) {
                processor.queueInput(input)
                val out = processor.output.order(ByteOrder.nativeOrder())
                while (out.hasRemaining()) {
                    val l = out.float; val r = out.float
                    assertTrue("${preset.id}: peak $l", l.isFinite() && abs(l) <= 0.891252f)
                    assertEquals("Stereo image must be linked", l * 0.5f, r, 0.00001f)
                    frames++
                }
            }
            assertEquals(48000, frames)
            processor.reset()
        }
    }

    @Test fun bypassPreservesAllBytesIncludingThirtyTwoBitAndBigEndian() {
        clear()
        for ((encoding, bytes) in listOf(C.ENCODING_PCM_16BIT to 2, C.ENCODING_PCM_24BIT to 3, C.ENCODING_PCM_32BIT to 4,
            C.ENCODING_PCM_FLOAT to 4, C.ENCODING_PCM_16BIT_BIG_ENDIAN to 2, C.ENCODING_PCM_24BIT_BIG_ENDIAN to 3, C.ENCODING_PCM_32BIT_BIG_ENDIAN to 4)) {
            val raw = ByteArray(bytes * 2 * 2048) { (it * 31).toByte() }
            val input = ByteBuffer.allocateDirect(raw.size).put(raw).also { it.flip() }
            val processor = EchoDspAudioProcessor(emptyArray())
            processor.configure(AudioProcessor.AudioFormat(96000, 2, encoding)); processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
            val actual = java.io.ByteArrayOutputStream()
            while (input.hasRemaining()) { processor.queueInput(input); val out = processor.output; val b = ByteArray(out.remaining()); out.get(b); actual.write(b) }
            assertArrayEquals(raw, actual.toByteArray())
            processor.reset()
        }
    }

    @Test fun crossfeedIsDelayedAndDoesNotProcessMono() {
        val kernel = EchoDspKernel()
        kernel.configure(48000)
        kernel.setTarget(EchoDspSettings(crossfeedEnabled = true, crossfeedAmount = 0.3f), 0f)
        kernel.reset()
        kernel.process(1f, 0f, true)
        assertEquals(0f, kernel.right, 0f)
        var fed = 0f
        repeat(100) { kernel.process(0f, 0f, true); fed += abs(kernel.right) }
        assertTrue(fed > 0.05f)
        kernel.reset()
        kernel.process(0.5f, 0.5f, false)
        assertEquals(0.5f, kernel.left, 0.000001f)
    }

    @Test fun gainReleaseAndCrossfeedSwitchAreSmoothAndFlushClearsHistory() {
        val kernel = EchoDspKernel()
        kernel.configure(48000); kernel.setTarget(EchoDspSettings(limiterEnabled = true), 0f); kernel.reset()
        kernel.process(2f, 1f, true)
        kernel.process(0.5f, 0.25f, true)
        val first = kernel.left
        repeat(48000) { kernel.process(0.5f, 0.25f, true) }
        assertTrue(first < kernel.left)
        assertEquals(0.5f, kernel.left, 0.001f)
        kernel.setTarget(EchoDspSettings(crossfeedEnabled = true), 0f)
        kernel.process(0.5f, 0f, true)
        assertTrue(abs(kernel.left - 0.5f) < 0.001f)
        kernel.reset(); kernel.process(0f, 0f, true)
        assertEquals(0f, kernel.left, 0f); assertEquals(0f, kernel.right, 0f)
    }
}
