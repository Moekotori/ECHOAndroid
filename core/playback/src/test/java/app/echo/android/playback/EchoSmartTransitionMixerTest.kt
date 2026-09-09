package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
class EchoSmartTransitionMixerTest {
    @Test
    fun inactiveWhenDisabled() {
        val mixer = EchoSmartTransitionMixer()
        val format = mixer.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT))
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, format)
        assertFalse(mixer.isActive)
    }

    @Test
    fun passThroughCopiesInput() {
        val mixer = EchoSmartTransitionMixer()
        mixer.setEnabled(true)
        mixer.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_FLOAT))
        mixer.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val input = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder())
        input.putFloat(0.2f).putFloat(-0.4f).putFloat(0.1f).putFloat(0.0f).flip()
        mixer.queueInput(input)
        val output = mixer.output.order(ByteOrder.nativeOrder())
        assertEquals(0.2f, output.float, 0.0001f)
        assertEquals(-0.4f, output.float, 0.0001f)
        assertEquals(0.1f, output.float, 0.0001f)
        assertEquals(0.0f, output.float, 0.0001f)
    }

    @Test
    fun mixStartsAtOutgoingAndEndsAtIncoming() {
        val mixer = EchoSmartTransitionMixer()
        mixer.setEnabled(true)
        mixer.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_FLOAT))
        mixer.flush(AudioProcessor.StreamMetadata.DEFAULT)
        mixer.arm(floatArrayOf(0.8f, 0.8f), frames = 2, channels = 1)
        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
        input.putFloat(0.5f).putFloat(0.5f).flip()
        mixer.queueInput(input)
        val output = mixer.output.order(ByteOrder.nativeOrder())
        val first = output.float
        val second = output.float
        assertEquals(0.5f, first, 0.02f)
        assertEquals(0.8f, second, 0.02f)
        assertFalse(mixer.mixing)
    }

    @Test
    fun incomingGainScalesTheIncomingDeckAtTheEnd() {
        val mixer = EchoSmartTransitionMixer()
        mixer.setEnabled(true)
        mixer.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_FLOAT))
        mixer.flush(AudioProcessor.StreamMetadata.DEFAULT)
        mixer.arm(floatArrayOf(0.8f), frames = 1, channels = 1, incomingGain = 0.5f)
        val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        input.putFloat(0.5f).flip()
        mixer.queueInput(input)
        val output = mixer.output.order(ByteOrder.nativeOrder()).float
        assertEquals(0.4f, output, 0.03f)
    }

    @Test
    fun holdFramesPassThroughBeforeMixing() {
        val mixer = EchoSmartTransitionMixer()
        mixer.setEnabled(true)
        mixer.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_FLOAT))
        mixer.flush(AudioProcessor.StreamMetadata.DEFAULT)
        mixer.arm(floatArrayOf(0.9f), frames = 1, channels = 1, holdFrames = 1)
        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
        input.putFloat(0.2f).putFloat(0.2f).flip()
        mixer.queueInput(input)
        val output = mixer.output.order(ByteOrder.nativeOrder())
        assertEquals(0.2f, output.float, 0.02f)
        assertEquals(0.9f, output.float, 0.02f)
        assertFalse(mixer.mixing)
    }

    @Test
    fun cancelStopsMixing() {
        val mixer = EchoSmartTransitionMixer()
        mixer.setEnabled(true)
        mixer.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_FLOAT))
        mixer.arm(FloatArray(8) { 1f }, 8, 1)
        assertTrue(mixer.mixing)
        mixer.cancel()
        assertFalse(mixer.mixing)
    }
}
