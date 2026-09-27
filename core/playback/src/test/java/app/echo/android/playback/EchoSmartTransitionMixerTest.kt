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

    @Test
    fun mixedIncomingFramesCountOnlyAfterHold() {
        val mixer = EchoSmartTransitionMixer()
        mixer.setEnabled(true)
        mixer.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_FLOAT))
        mixer.flush(AudioProcessor.StreamMetadata.DEFAULT)
        mixer.arm(floatArrayOf(0.9f, 0.8f), frames = 2, channels = 1, holdFrames = 1)
        assertEquals(0, mixer.mixedIncomingFrames)
        val input = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder())
        input.putFloat(0.2f).putFloat(0.3f).putFloat(0.4f).flip()
        mixer.queueInput(input)
        assertEquals(2, mixer.mixedIncomingFrames)
        assertFalse(mixer.mixing)
    }

    @Test
    fun schedulesAgainstPcmPositionAfterSeekAndNeverResurrectsOutgoingTail() {
        val mixer = readyMixer()
        mixer.flush(AudioProcessor.StreamMetadata(10_000_000))
        val handle = mixer.arm(FloatArray(48) { 0.8f }, 48, 1, startPositionMs = 10_001)!!
        val out = feed(mixer, 100, 0.2f)
        assertEquals(0.2f, out[47], 0.0001f)
        assertEquals(0.2f, out[48], 0.0001f)
        assertEquals(0.8f, out[95], 0.0001f)
        assertEquals(0f, out[96], 0f)
        assertEquals(48, handle.mixedFrames)
        assertEquals(0f, feed(mixer, 1, 0.9f)[0], 0f)
        mixer.flush(AudioProcessor.StreamMetadata.DEFAULT)
        assertEquals(0.3f, feed(mixer, 1, 0.3f)[0], 0.0001f)
    }

    @Test
    fun consecutiveHandoffsKeepIndependentProgressAndFlushRejectsOldPlans() {
        val mixer = readyMixer()
        val first = mixer.arm(FloatArray(48) { 0.4f }, 48, 1)!!
        feed(mixer, 48, 0.2f)
        val oldEpoch = mixer.streamEpoch
        mixer.flush(AudioProcessor.StreamMetadata(1_000_000))
        assertEquals(null, mixer.arm(FloatArray(48), 48, 1, expectedEpoch = oldEpoch))
        val second = mixer.arm(FloatArray(48) { 0.6f }, 48, 1)!!
        feed(mixer, 24, 0.4f)
        assertEquals(48, first.mixedFrames)
        assertEquals(24, second.mixedFrames)
        mixer.cancel()
        assertEquals(0.4f, feed(mixer, 24, 0.4f)[0], 0.0001f)
        assertEquals(24, second.mixedFrames)
    }

    @Test
    fun surroundIsBypassedAndInvalidBufferCannotBeArmed() {
        val mixer = EchoSmartTransitionMixer()
        mixer.setEnabled(true)
        assertEquals(AudioProcessor.AudioFormat.NOT_SET,
            mixer.configure(AudioProcessor.AudioFormat(48_000, 6, C.ENCODING_PCM_FLOAT)))
        assertEquals(null, mixer.arm(floatArrayOf(0f), 4, 2))
    }

    private fun readyMixer() = EchoSmartTransitionMixer().also {
        it.setEnabled(true)
        it.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_FLOAT))
        it.flush(AudioProcessor.StreamMetadata.DEFAULT)
    }

    private fun feed(mixer: EchoSmartTransitionMixer, frames: Int, value: Float): FloatArray {
        val buffer = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        repeat(frames) { buffer.putFloat(value) }
        buffer.flip()
        mixer.queueInput(buffer)
        val output = mixer.output.order(ByteOrder.nativeOrder())
        return FloatArray(output.remaining() / 4) { output.float }
    }
}
