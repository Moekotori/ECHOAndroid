package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class EchoUsbFormatAudioProcessorTest {
    private class Converter : UsbResamplerBackend {
        override val available = true
        var closes = 0
        var tailRead = false
        override fun create(inRate: Int, inChannels: Int, inBits: Int, outRate: Int, outChannels: Int, outBits: Int): Long {
            tailRead = false
            return 1L
        }
        override fun convert(handle: Long, input: ByteBuffer?, offset: Int, frames: Int,
            bigEndian: Boolean, output: ByteBuffer, capacity: Int): Int {
            if (input == null && tailRead) return 0
            output.putInt(0, if (input == null) 5678 else 1234)
            if (input == null) tailRead = true
            return 4
        }
        override fun close(handle: Long) { closes++ }
    }

    @Test fun eosKeepsReturnedOutputIntactThenDrainsAndFlushReleasesOldContext() {
        val converter = Converter()
        val processor = EchoUsbFormatAudioProcessor({ AudioProcessor.AudioFormat(48000, 1, C.ENCODING_PCM_16BIT) }, converter)
        processor.configure(AudioProcessor.AudioFormat(44100, 1, C.ENCODING_PCM_16BIT))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        processor.queueInput(ByteBuffer.allocateDirect(4))
        val first = processor.output
        assertEquals(1234, first.getInt(0))
        processor.queueEndOfStream()
        assertEquals(1234, first.getInt(0)) // EOS must not overwrite the buffer still held by the sink.
        assertFalse(processor.isEnded)
        first.position(first.limit())
        val tail = processor.output
        assertEquals(5678, tail.int)
        processor.output
        assertTrue(processor.isEnded)
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        assertEquals(1, converter.closes)
        processor.reset()
        assertEquals(2, converter.closes)
    }
}
