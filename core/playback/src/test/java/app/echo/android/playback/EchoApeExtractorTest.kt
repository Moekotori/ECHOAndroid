package app.echo.android.playback

import androidx.media3.common.ParserException
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.PositionHolder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

@UnstableApi
class EchoApeExtractorTest {
    @Test fun modernFramesKeepWordAlignmentAndSeekBackToFrame() {
        val file = fixture(3990)
        val extractor = EchoApeExtractor()
        val output = CaptureOutput()
        var input = BufferExtractorInput(file)
        assertTrue(extractor.sniff(input))
        input.resetPeekPosition()
        extractor.init(output)
        val seek = PositionHolder()
        repeat(20) {
            when (extractor.read(input, seek)) {
                Extractor.RESULT_SEEK -> input = BufferExtractorInput(file, seek.position.toInt())
                Extractor.RESULT_END_OF_INPUT -> return@repeat
            }
        }
        assertEquals("audio/ape", output.track.format!!.sampleMimeType)
        assertEquals(24, androidx.media3.common.util.Util.getPcmFrameSize(output.track.format!!.pcmEncoding, 1) * 8)
        assertArrayEquals(byteArrayOf(0x96.toByte(), 15, 0xd0.toByte(), 7, 0, 0), output.track.format!!.initializationData.single())
        assertEquals(2, output.track.sampleBytes.size)
        val last = ByteBuffer.wrap(output.track.sampleBytes.last()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(16, last.int)
        assertEquals(1, last.int)
        assertEquals(0L, output.track.sampleTimes.first())
        val point = output.seekMap!!.getSeekPoints(7_000_000).first
        assertEquals(92L, point.position)
        extractor.seek(point.position, point.timeUs)
        input = BufferExtractorInput(file, point.position.toInt())
        assertEquals(Extractor.RESULT_CONTINUE, extractor.read(input, seek))
        assertEquals(Extractor.RESULT_END_OF_INPUT, extractor.read(input, seek))
        assertArrayEquals(output.track.sampleBytes[1], output.track.sampleBytes[2])
    }

    @Test fun legacyHeaderAndLeadingId3AreRecognized() {
        val tag = byteArrayOf(73, 68, 51, 4, 0, 0, 0, 0, 0, 2, 0, 0)
        for (version in listOf(3800, 3970, 3990)) {
            val bytes = tag + fixture(version)
            val input = BufferExtractorInput(bytes)
            assertTrue(EchoApeExtractor().sniff(input))
            input.resetPeekPosition()
            val layout = readApeLayout(input)
            assertEquals(version, layout.version)
            assertEquals(44_100, layout.sampleRate)
            assertEquals(if (version < 3950) 9216 else 294_912, layout.blocksPerFrame)
            assertEquals(12L + if (version >= 3980) 84 else if (version < 3810) 42 else 40, layout.positions.first())
        }
    }

    @Test fun corruptSeekTableAndOversizedHeadersAreRejected() {
        val bytes = fixture(3990)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(80, 1)
        try {
            readApeLayout(BufferExtractorInput(bytes))
            fail("Corrupt seek table was accepted")
        } catch (_: ParserException) { }
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(8, Int.MAX_VALUE)
        try {
            readApeLayout(BufferExtractorInput(bytes))
            fail("Oversized descriptor was accepted")
        } catch (_: ParserException) { }
        assertFalse(EchoApeExtractor().sniff(BufferExtractorInput(byteArrayOf(1, 2, 3))))
    }

    private fun fixture(version: Int): ByteArray {
        val first = if (version >= 3980) 84 else if (version < 3810) 42 else 40
        val buffer = ByteBuffer.allocate(first + 17).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("MAC ".toByteArray()).putShort(version.toShort())
        if (version >= 3980) {
            buffer.putShort(0).putInt(52).putInt(24).putInt(8).putInt(0)
                .putInt(17).putInt(0).putInt(0).put(ByteArray(16))
            buffer.putShort(2000).putShort(0).putInt(294_912).putInt(16).putInt(2)
                .putShort(24).putShort(2).putInt(44_100)
        } else {
            buffer.putShort(2000).putShort(32).putShort(2).putInt(44_100)
                .putInt(0).putInt(0).putInt(2).putInt(16)
        }
        buffer.putInt(first).putInt(first + 9)
        if (version < 3810) buffer.putShort(0)
        repeat(17) { buffer.put(it.toByte()) }
        return buffer.array()
    }
}
