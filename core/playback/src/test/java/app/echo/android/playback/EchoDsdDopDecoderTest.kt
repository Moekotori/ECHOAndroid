package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class EchoDsdDopDecoderTest {
    @Test fun workerDrainsCarriedDsdBeforeEosAndAfterFlush() {
        val decoder = EchoDsdDopDecoder(Format.Builder().setSampleMimeType(EchoDsdMime.MsbfPlanar)
            .setChannelCount(2).setSampleRate(352_800).setMaxInputSize(6).build())
        try {
            repeat(2) { iteration ->
                val start = iteration * 1_000_000L
                decoder.setOutputStartTimeUs(start)
                decoder.dequeueInputBuffer()!!.let { input ->
                    input.ensureSpaceForWrite(6)
                    input.data!!.put(byteArrayOf(1, 2, 3, 11, 12, 13))
                    input.timeUs = start
                    input.flip()
                    decoder.queueInputBuffer(input)
                }
                decoder.dequeueInputBuffer()!!.let { input ->
                    input.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
                    decoder.queueInputBuffer(input)
                }
                val bytes = ByteArrayOutputStream()
                var ended = false
                val deadline = System.nanoTime() + 3_000_000_000L
                while (!ended && System.nanoTime() < deadline) {
                    val output = decoder.dequeueOutputBuffer()
                    if (output == null) { Thread.sleep(1); continue }
                    ended = output.isEndOfStream
                    if (!ended) output.data?.let { data ->
                        val packet = ByteArray(data.remaining()); data.get(packet); bytes.write(packet)
                    }
                    output.release()
                }
                assertTrue("Decoder did not emit EOS", ended)
                assertArrayEquals(byteArrayOf(2, 1, 5, 12, 11, 5, 0x69, 3, 0xFA.toByte(),
                    0x69, 13, 0xFA.toByte()), bytes.toByteArray())
                decoder.flush()
            }
        } finally { decoder.release() }
    }
}
