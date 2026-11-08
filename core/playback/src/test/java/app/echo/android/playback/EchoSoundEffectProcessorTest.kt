package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test

@UnstableApi
class EchoSoundEffectProcessorTest {
    @Test fun bitPerfectDiagnosticsReportBypassInsteadOfThePreparedEffect() {
        val running = EchoSoundEffectRuntimeStatus(EchoSoundEffectState.Active, EchoSoundEffectPreset.Hall, 48000, 2)
        assertEquals(EchoSoundEffectState.Bypassed, EchoPlaybackDiagnostics(usbBitPerfectEnabled = true).withSoundEffectRuntime(running).soundEffect.state)
        assertEquals(running, EchoPlaybackDiagnostics().withSoundEffectRuntime(running).soundEffect)
    }
    private class Backend : EchoSoundEffectBackend {
        var next = 0L
        val created = mutableListOf<String>()
        val destroyed = mutableSetOf<Long>()
        var tail = 0
        override fun create(preset: String, rate: Int, channels: Int): Long { created += preset; return ++next }
        override fun process(handle: Long, samples: ByteBuffer, frames: Int, channels: Int, enabled: Boolean, metadata: IntArray): Boolean {
            assertFalse(destroyed.contains(handle))
            var input = false
            repeat(frames * channels) { i ->
                val offset = samples.position() + i * 4
                val value = samples.getFloat(offset); input = input || value != 0f
                samples.putFloat(offset, if (enabled) value * .5f else value)
            }
            tail = if (input) 4 else (tail - frames).coerceAtLeast(0)
            metadata[0] = tail; metadata[1] = 0; metadata[2] = if (enabled) 0 else 1
            return true
        }
        override fun reset(handle: Long) { tail = 0 }
        override fun destroy(handle: Long) { assertTrue(destroyed.add(handle)) }
    }
    private val direct = Executor { it.run() }
    private fun samples(frames: Int) = ByteBuffer.allocateDirect(frames * 8).order(ByteOrder.nativeOrder()).apply {
        repeat(frames * 2) { putFloat(.25f) }; flip()
    }

    @Test fun switchingAndClosingReclaimEachPreparedGraphExactlyOnce() {
        val native = Backend(); val effect = EchoSoundEffectProcessor(native, direct)
        effect.configure(48000, 2)
        effect.update(EchoSoundEffectSettings(true, EchoSoundEffectPreset.Tape))
        assertTrue(effect.process(samples(8), 8))
        assertEquals(EchoSoundEffectState.Active, EchoSoundEffectRuntime.status.value.state)
        effect.update(EchoSoundEffectSettings(true, EchoSoundEffectPreset.Hall))
        effect.process(samples(8), 8) // old graph fades to dry
        effect.process(samples(8), 8) // adopt prepared hall
        assertEquals(EchoSoundEffectPreset.Hall, EchoSoundEffectRuntime.status.value.preset)
        effect.close()
        assertEquals(setOf(1L, 2L), native.destroyed)
        assertFalse(effect.needsProcessing)
    }

    @Test fun rapidSelectionPreparesOnlyTheLatestRequestAndUnsupportedFormatsBypass() {
        val jobs = mutableListOf<Runnable>(); val native = Backend()
        val effect = EchoSoundEffectProcessor(native, Executor { jobs += it })
        effect.configure(48000, 2)
        effect.update(EchoSoundEffectSettings(true, EchoSoundEffectPreset.Tape))
        effect.update(EchoSoundEffectSettings(true, EchoSoundEffectPreset.Vinyl))
        assertEquals(1, jobs.size)
        jobs.removeAt(0).run()
        assertEquals(listOf("vinyl"), native.created)
        effect.process(samples(8), 8); effect.close()
        while (jobs.isNotEmpty()) jobs.removeAt(0).run()
        effect.configure(384000, 2); effect.update(EchoSoundEffectSettings(true))
        assertEquals(EchoSoundEffectState.UnsupportedFormat, EchoSoundEffectRuntime.status.value.state)
        assertFalse(effect.needsProcessing)
    }

    @Test fun endOfStreamDrainsABoundedTailAndFlushClearsIt() {
        val native = Backend(); val effect = EchoSoundEffectProcessor(native, direct)
        val processor = EchoDspAudioProcessor(emptyArray(), effect)
        EchoPlaybackProcessRuntime.setDspSettings(EchoDspSettings(soundEffect = EchoSoundEffectSettings(true)))
        try {
            processor.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT))
            processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
            processor.queueInput(samples(8)); processor.output
            processor.queueEndOfStream()
            assertFalse(processor.isEnded)
            assertEquals(4 * 8, processor.output.remaining())
            assertTrue(processor.isEnded)
            processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
            assertEquals(0, effect.tailFrames)
        } finally { processor.reset(); EchoPlaybackProcessRuntime.setDspSettings(EchoDspSettings()) }
    }

    @Test fun tailsNeverAddPauseAtGaplessRepeatOrMidTrackReconfiguration() {
        val end = EchoPlaybackSurfaceSnapshot(hasTrack = true, durationMs = 1000)
        assertTrue(shouldDrainSoundEffectTail(end, 1_000_000))
        assertFalse(shouldDrainSoundEffectTail(end.copy(hasNextTrack = true), 1_000_000))
        assertFalse(shouldDrainSoundEffectTail(end.copy(repeatMode = 1), 1_000_000))
        assertFalse(shouldDrainSoundEffectTail(end, 100_000))
    }
}
