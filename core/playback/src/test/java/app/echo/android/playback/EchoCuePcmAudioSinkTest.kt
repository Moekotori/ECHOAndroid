package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

@UnstableApi
class EchoCuePcmAudioSinkTest {
    private fun format(clip: EchoCueClip?) = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setPcmEncoding(C.ENCODING_PCM_16BIT).setSampleRate(16_000).setChannelCount(1).setCustomData(clip).build()
    private fun samples(count: Int) = ByteBuffer.allocateDirect(count * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
        repeat(count) { putShort(it.toShort()) }; flip()
    }
    private fun sink(write: (ByteBuffer, Long) -> Boolean): EchoCuePcmAudioSink {
        val delegate = Proxy.newProxyInstance(AudioSink::class.java.classLoader, arrayOf(AudioSink::class.java)) { _, method, args ->
            if (method.name == "handleBuffer") write(args[0] as ByteBuffer, args[1] as Long) else null
        } as AudioSink
        return EchoCuePcmAudioSink(delegate)
    }

    @Test fun trimsThePartialEndBlockAndConsumesItsDroppedTail() {
        var count = 0
        val output = sink { buffer, _ -> count += buffer.remaining() / 2; buffer.position(buffer.limit()); true }
        val clip = EchoCueClip("file#cue:1", "file", 0, 93_000)
        // DecoderAudioRenderer propagates the tagged input format's opaque data to the sink.
        output.configure(format(clip).buildUpon().setCustomData(EchoCueFormat(clip, format(null))).build(), 0, null)
        val input = samples(1600)
        assertTrue(output.handleBuffer(input, 0, 1))
        assertEquals(1488, count)
        assertEquals(3200, input.position())
        assertEquals(3200, input.limit())
    }

    @Test fun partialWritesKeepTheTrimmedWindowAndTimestampUntilConsumed() {
        val seen = ArrayList<Short>()
        var writes = 0
        val output = sink { buffer, pts ->
            assertEquals(1_093_000L, pts)
            val count = if (writes++ == 0) 20 else buffer.remaining() / 2
            repeat(count) { seen += buffer.short }
            !buffer.hasRemaining()
        }
        output.configure(format(EchoCueClip("file#cue:2", "file", 93_000, 100_000)), 0, null)
        output.setOutputStreamOffsetUs(1_000_000L)
        val input = samples(320)
        assertFalse(output.handleBuffer(input, 1_080_000L, 1))
        assertTrue(output.handleBuffer(input, 1_080_000L, 1))
        assertEquals((208 until 320).map(Int::toShort), seen)
        assertEquals(640, input.position())
    }

    @Test fun flushReleasesTheOldWindowAndOrdinaryStreamsPassThrough() {
        var count = 0
        val output = sink { buffer, _ -> count += buffer.remaining() / 2; false }
        output.configure(format(EchoCueClip("file#cue:1", "file", 0, 93_000)), 0, null)
        val input = samples(1600)
        assertFalse(output.handleBuffer(input, 0, 1))
        assertEquals(2976, input.limit())
        output.flush()
        assertEquals(3200, input.limit())
        output.configure(format(null), 0, null)
        output.handleBuffer(samples(10), 0, 1)
        assertEquals(1498, count)
    }
}
