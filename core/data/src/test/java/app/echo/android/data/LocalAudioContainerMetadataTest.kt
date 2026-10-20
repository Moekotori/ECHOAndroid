package app.echo.android.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class LocalAudioContainerMetadataTest {
    // Short FFmpeg-generated fixtures, independently checked with ffprobe.
    @Test fun realContainersReportRateAndDuration() {
        for (name in listOf("pcm.wav", "pcm.aiff", "little-endian.aifc", "tagged.wv", "tagged.wma")) {
            val bytes = fixture(name)
            val input = bytes.inputStream()
            val info = LocalAudioContainerMetadata.read(input)!!
            assertEquals(name, 48000, info.sampleRateHz)
            assertTrue("$name duration=${info.durationMs}", info.durationMs in 240L..310L)
            assertTrue("$name should stop before audio packets", input.available() > 0)
            if (name.endsWith("wma")) {
                assertEquals("扫描样本", info.tags?.title)
                assertEquals("ECHO", info.tags?.artist)
                assertEquals("Scan fixtures", info.tags?.album)
                assertEquals(3, info.tags?.trackNumber)
                assertEquals("扫描样本", readLocalAudioTags(bytes.inputStream())?.title)
            }
        }
    }

    @Test fun wavPackFileTagsReachTheSharedTagReader() {
        val bytes = fixture("tagged.wv")
        val tags = readLocalAudioTags(RandomAccessTagInput(bytes.size.toLong()) { at, count ->
            bytes.copyOfRange(at.toInt(), at.toInt() + count)
        })!!
        assertEquals("扫描样本", tags.title)
        assertEquals("ECHO", tags.artist)
        assertEquals(3, tags.trackNumber)
    }

    @Test fun malformedAndOversizedHeadersAreRejected() {
        assertNull(LocalAudioContainerMetadata.read(byteArrayOf(1, 2, 3).inputStream()))
        val asf = fixture("tagged.wma")
        ByteBuffer.wrap(asf).order(ByteOrder.LITTLE_ENDIAN).putLong(16, Long.MAX_VALUE)
        assertNull(LocalAudioContainerMetadata.read(asf.inputStream()))
        val aiff = fixture("pcm.aiff")
        // Huge chunk length cannot trigger an allocation or walk audio data.
        ByteBuffer.wrap(aiff).order(ByteOrder.BIG_ENDIAN).putInt(16, Int.MAX_VALUE)
        assertNull(LocalAudioContainerMetadata.read(aiff.inputStream()))
    }

    @Test fun wavPackCustomRateAndUnknownDurationStayDistinct() {
        val header = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
            .put("wvpk".toByteArray()).putInt(30).putShort(0x410).put(0).put(0)
            .putInt(-1).putInt(0).putInt(12000).putInt(15 shl 23).putInt(0).array()
        // Optional ID_SAMPLE_RATE stores a custom 24-bit little-endian rate.
        val info = LocalAudioContainerMetadata.read((header + byteArrayOf(0x67, 2, 0x80.toByte(), 0xbb.toByte(), 0, 0)).inputStream())!!
        assertEquals(48000, info.sampleRateHz)
        assertEquals(0L, info.durationMs)
        header[27] = 0x80.toByte()
        assertNull(LocalAudioContainerMetadata.read(header.inputStream()))
    }

    @Test fun rf64TechnicalInfoUses64BitDataSize() {
        val bytes = fixture("pcm.wav")
        val fmtAt = bytes.indices.first { it <= bytes.size - 4 && bytes.decodeToString(it, it + 4) == "fmt " }
        val fmt = bytes.copyOfRange(fmtAt, fmtAt + 24)
        val dataSize = 0x1_0000_0000L
        val ds = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN).put("ds64".toByteArray())
            .putInt(28).putLong(dataSize + 72).putLong(dataSize).putLong(0).putInt(0).array()
        val input = ("RF64".toByteArray() + byteArrayOf(-1,-1,-1,-1) + "WAVE".toByteArray() + ds + fmt +
            "data".toByteArray() + byteArrayOf(-1,-1,-1,-1)).inputStream()
        val info = LocalAudioContainerMetadata.read(input)!!
        assertEquals(dataSize * 1000 / 96000, info.durationMs)
    }

    private fun fixture(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/audio-scan/$name")).use { it.readBytes() }
}
