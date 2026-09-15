package app.echo.android.playback

import android.media.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class EchoSmartTransitionPcmTest {
    @Test
    fun identityResampleCopiesInterleavedFrames() {
        val input = floatArrayOf(0.1f, -0.2f, 0.3f, -0.4f)
        val output = EchoSmartTransitionPcm.resampleInterleaved(input, 48_000, 2, 48_000, 2)
        assertEquals(input.size, output.size)
        input.indices.forEach { index -> assertEquals(input[index], output[index], 0.0001f) }
    }

    @Test
    fun resample44100To48000KeepsDurationAndEndpoints() {
        val inputRate = 44_100
        val outputRate = 48_000
        val frames = 441
        val input = FloatArray(frames * 2) { index -> if (index % 2 == 0) 0.5f else -0.25f }
        val output = EchoSmartTransitionPcm.resampleInterleaved(input, inputRate, 2, outputRate, 2)
        val outFrames = output.size / 2
        assertEquals(480, outFrames)
        assertEquals(0.5f, output[0], 0.001f)
        assertEquals(-0.25f, output[1], 0.001f)
        assertEquals(0.5f, output[output.size - 2], 0.001f)
        assertEquals(-0.25f, output[output.size - 1], 0.001f)
    }

    @Test
    fun monoToStereoDuplicatesTheChannel() {
        val output = EchoSmartTransitionPcm.resampleInterleaved(floatArrayOf(0.4f, 0.6f), 48_000, 1, 48_000, 2)
        assertEquals(4, output.size)
        assertEquals(0.4f, output[0], 0.0001f)
        assertEquals(0.4f, output[1], 0.0001f)
        assertEquals(0.6f, output[2], 0.0001f)
        assertEquals(0.6f, output[3], 0.0001f)
    }

    @Test
    fun readsSixteenTwentyFourThirtyTwoAndFloatEncodings() {
        val pcm16 = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
        pcm16.putShort(16_384).flip()
        assertEquals(0.5f, EchoSmartTransitionPcm.readSample(pcm16, AudioFormat.ENCODING_PCM_16BIT), 0.01f)

        val pcm24 = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
        val packed24 = (0.25f * 8_388_608f).toInt()
        pcm24.put(packed24.toByte()).put((packed24 shr 8).toByte()).put((packed24 shr 16).toByte()).flip()
        assertEquals(0.25f, EchoSmartTransitionPcm.readSample(pcm24, AudioFormat.ENCODING_PCM_24BIT_PACKED), 0.02f)

        val pcm32 = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        pcm32.putInt((0.5f * 2_147_483_647f).toInt()).flip()
        assertEquals(0.5f, EchoSmartTransitionPcm.readSample(pcm32, AudioFormat.ENCODING_PCM_32BIT), 0.02f)

        val pcmFloat = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        pcmFloat.putFloat(-0.75f).flip()
        assertEquals(-0.75f, EchoSmartTransitionPcm.readSample(pcmFloat, AudioFormat.ENCODING_PCM_FLOAT), 0.0001f)
        assertTrue(abs(EchoSmartTransitionPcm.bytesPerSample(AudioFormat.ENCODING_PCM_24BIT_PACKED) - 3) < 1)
    }
}
