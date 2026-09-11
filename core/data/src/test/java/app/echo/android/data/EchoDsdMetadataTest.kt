package app.echo.android.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoDsdMetadataTest {
    @Test
    fun dsfDurationAndSampleRateComeFromFmtChunk() {
        val info = EchoDsdMetadata.read(ByteArrayInputStream(dsf(realBytesPerChannel = 64)))!!
        assertEquals(2_822_400, info.sampleRateHz)
        assertEquals((64L * 8 * 1000L) / 2_822_400L, info.durationMs)
        assertFalse(info.compressed)
    }

    @Test
    fun dsfId3TagsAreReadFromMetadataOffset() {
        val id3 = id3Title("Helios")
        val info = EchoDsdMetadata.read(ByteArrayInputStream(dsf(id3 = id3)))!!
        assertEquals("Helios", info.tags?.title)
    }

    @Test
    fun dffDurationUsesDataChunkSize() {
        val info = EchoDsdMetadata.read(ByteArrayInputStream(dff(frames = 64)))!!
        assertEquals(2_822_400, info.sampleRateHz)
        assertEquals((64L * 8000L) / 2_822_400L, info.durationMs)
        assertFalse(info.compressed)
    }

    @Test
    fun dstDffIsMarkedCompressed() {
        val info = EchoDsdMetadata.read(ByteArrayInputStream(dff(compressed = true)))
        assertNotNull(info)
        assertTrue(info!!.compressed)
    }

    @Test
    fun isDsdMatchesLibraryMimeAndExtension() {
        assertTrue(EchoDsdMetadata.isDsd("audio/dsf", "a.flac"))
        assertTrue(EchoDsdMetadata.isDsd("audio/mpeg", "album/track.dff"))
        assertFalse(EchoDsdMetadata.isDsd("audio/flac", "song.flac"))
    }

    private fun dsf(
        channels: Int = 2,
        blockSize: Int = 64,
        realBytesPerChannel: Int = 64,
        id3: ByteArray? = null,
    ): ByteArray {
        val dataSize = blockSize.toLong() * channels
        val sampleCount = realBytesPerChannel.toLong() * 8L
        val id3Offset = if (id3 != null) 92L + dataSize else 0L
        val fileSize = 92L + dataSize + (id3?.size ?: 0)
        val out = ByteArrayOutputStream()
        out.write("DSD ".toByteArray(Charsets.ISO_8859_1))
        out.write(u64le(28))
        out.write(u64le(fileSize))
        out.write(u64le(id3Offset))
        out.write("fmt ".toByteArray(Charsets.ISO_8859_1))
        out.write(u64le(52))
        out.write(u32le(1))
        out.write(u32le(0))
        out.write(u32le(2))
        out.write(u32le(channels))
        out.write(u32le(2_822_400))
        out.write(u32le(8))
        out.write(u64le(sampleCount))
        out.write(u32le(blockSize))
        out.write(u32le(0))
        out.write("data".toByteArray(Charsets.ISO_8859_1))
        out.write(u64le(dataSize + 12))
        repeat(channels) {
            out.write(ByteArray(realBytesPerChannel) { 0x69 })
            out.write(ByteArray((blockSize - realBytesPerChannel).coerceAtLeast(0)))
        }
        id3?.let(out::write)
        return out.toByteArray()
    }

    private fun dff(frames: Int = 64, compressed: Boolean = false): ByteArray {
        val data = ByteArray(frames * 2) { 0x69 }
        val fs = chunk("FS  ", u32be(2_822_400))
        val chnl = chunk("CHNL", u16be(2) + "SLFT".toByteArray() + "SRGT".toByteArray())
        val cmpr = chunk("CMPR", (if (compressed) "DST " else "DSD ").toByteArray())
        val prop = chunk("PROP", "SND ".toByteArray() + fs + chnl + cmpr)
        val audio = chunk(if (compressed) "DST " else "DSD ", data)
        val body = "DSD ".toByteArray() + prop + audio
        return "FRM8".toByteArray() + u64be(body.size.toLong()) + body
    }

    private fun chunk(id: String, payload: ByteArray): ByteArray {
        val pad = if (payload.size % 2 == 1) byteArrayOf(0) else byteArrayOf()
        return id.toByteArray() + u64be(payload.size.toLong()) + payload + pad
    }

    private fun id3Title(title: String): ByteArray {
        val payload = byteArrayOf(3) + title.toByteArray(Charsets.UTF_8) + byteArrayOf(0)
        val frame = "TIT2".toByteArray() + u32be(payload.size) + byteArrayOf(0, 0) + payload
        val tagSize = synchsafe(frame.size)
        return "ID3".toByteArray() + byteArrayOf(4, 0, 0) + tagSize + frame
    }

    private fun synchsafe(value: Int): ByteArray = byteArrayOf(
        ((value shr 21) and 0x7F).toByte(),
        ((value shr 14) and 0x7F).toByte(),
        ((value shr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte(),
    )

    private fun u32le(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    private fun u64le(value: Long): ByteArray = u32le(value.toInt()) + u32le((value ushr 32).toInt())

    private fun u16be(value: Int): ByteArray = byteArrayOf(
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    private fun u32be(value: Int): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    private fun u64be(value: Long): ByteArray = u32be((value ushr 32).toInt()) + u32be(value.toInt())
}
