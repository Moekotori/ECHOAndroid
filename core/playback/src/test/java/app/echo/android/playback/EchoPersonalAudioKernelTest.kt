package app.echo.android.playback

import app.echo.android.model.playback.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class EchoPersonalAudioKernelTest {
    private fun buffer(frames: Int, fill: (Int, Int) -> Float): ByteBuffer =
        ByteBuffer.allocateDirect(frames * 8).order(ByteOrder.nativeOrder()).apply {
            repeat(frames) { frame -> repeat(2) { ear -> putFloat(fill(frame, ear)) } }; flip()
        }

    @Test fun hrtfHasMeasuredCrossEarResponseAndMirrorsStereo() {
        fun impulse(ear: Int): FloatArray {
            val k = EchoPersonalAudioKernel().apply { configure(48000); setTarget(EchoDspSettings(hrtfEnabled = true), EchoVolumeSettings()) }
            repeat(20) { k.process(buffer(1024) { _, _ -> 0f }, 1024, 2) }
            val b = buffer(512) { frame, channel -> if (frame == 0 && channel == ear) 1f else 0f }
            k.process(b, 512, 2)
            return FloatArray(1024) { b.getFloat(it * 4) }
        }
        val left = impulse(0); val right = impulse(1)
        assertTrue(left.indices.any { it % 2 == 1 && abs(left[it]) > 0.001f })
        left.indices.step(2).forEach { i ->
            assertEquals(left[i], right[i + 1], 0.00001f)
            assertEquals(left[i + 1], right[i], 0.00001f)
        }
        assertTrue(left.all { it.isFinite() && abs(it) <= 1f })
    }

    @Test fun loudnessUsesAppVolumeAndLeavesReferenceNeutral() {
        fun gain(frequency: Double, volume: Float): Double {
            val k = EchoPersonalAudioKernel().apply { configure(48000)
                setTarget(EchoDspSettings(equalLoudnessEnabled = true), EchoVolumeSettings(levelDb = volume)) }
            var output = 0.0; var input = 0.0
            repeat(20) { block ->
                val b = buffer(1024) { i, _ -> (0.01 * sin(2 * PI * frequency * (block * 1024 + i) / 48000)).toFloat() }
                val raw = FloatArray(1024) { b.getFloat(it * 8) }
                k.process(b, 1024, 2)
                if (block >= 15) repeat(1024) { output += b.getFloat(it * 8).toDouble().pow(2); input += raw[it].toDouble().pow(2) }
            }
            return sqrt(output/input)
        }
        assertEquals(1.0, gain(30.0, -10f), 0.00001)
        assertTrue(gain(30.0, -30f) > gain(1000.0, -30f) * 1.3)
        assertTrue(gain(12000.0, -30f) > gain(1000.0, -30f) * 1.1)
    }

    @Test fun resetClearsResponseAndUnsupportedChannelsRemainUntouched() {
        val k = EchoPersonalAudioKernel().apply { configure(44100); setTarget(EchoDspSettings(hrtfEnabled = true), EchoVolumeSettings()) }
        k.process(buffer(256) { _, _ -> 0.3f }, 256, 2)
        k.reset()
        val silent = buffer(256) { _, _ -> 0f }
        k.process(silent, 256, 2)
        assertTrue((0 until 512).all { silent.getFloat(it * 4) == 0f })
        val mono = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).putFloat(0.25f).also { it.flip() }
        k.process(mono, 1, 1)
        assertEquals(0.25f, mono.getFloat(0), 0f)
    }

    @Test fun normalizingDisablesCrossfeedAndBoundsUntrustedValues() {
        val s = EchoDspSettings(crossfeedEnabled = true, hrtfEnabled = true, hrtfProfile = 9, hrtfAmount = Float.NaN,
            equalLoudnessBassDb = Float.POSITIVE_INFINITY).normalized
        assertFalse(s.crossfeedEnabled); assertEquals(2, s.hrtfProfile)
        assertEquals(1f, s.hrtfAmount, 0f); assertEquals(6f, s.equalLoudnessBassDb, 0f)
    }
}
