package app.echo.android.playback

import java.io.ByteArrayOutputStream

internal object EchoDsdFixtures {
    fun dsf(
        channels: Int = 2,
        dsdRateHz: Int = 2_822_400,
        blockSize: Int = 64,
        realBytesPerChannel: Int = 64,
        bitsPerSample: Int = 8,
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
        out.write(u32le(if (channels == 2) 2 else 1))
        out.write(u32le(channels))
        out.write(u32le(dsdRateHz))
        out.write(u32le(bitsPerSample))
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

    fun dff(
        channels: Int = 2,
        dsdRateHz: Int = 2_822_400,
        frames: Int = 64,
        compressed: Boolean = false,
    ): ByteArray {
        val data = ByteArray(frames * channels) { 0x69 }
        val fs = dffChunk("FS  ", u32be(dsdRateHz))
        val channelIds = ByteArrayOutputStream()
        channelIds.write(u16be(channels))
        val names = listOf("SLFT", "SRGT", "C   ", "LFE ", "LS  ", "RS  ")
        repeat(channels) { index ->
            channelIds.write(names[index].toByteArray(Charsets.ISO_8859_1))
        }
        val chnl = dffChunk("CHNL", channelIds.toByteArray())
        val cmpr = dffChunk("CMPR", (if (compressed) "DST " else "DSD ").toByteArray(Charsets.ISO_8859_1))
        val prop = dffChunk("PROP", "SND ".toByteArray(Charsets.ISO_8859_1) + fs + chnl + cmpr)
        val bodyTag = if (compressed) "DST " else "DSD "
        val audio = dffChunk(bodyTag, data)
        val body = "DSD ".toByteArray(Charsets.ISO_8859_1) + prop + audio
        return "FRM8".toByteArray(Charsets.ISO_8859_1) + u64be(body.size.toLong()) + body
    }

    private fun dffChunk(id: String, payload: ByteArray): ByteArray {
        val pad = if (payload.size % 2 == 1) byteArrayOf(0) else byteArrayOf()
        return id.toByteArray(Charsets.ISO_8859_1) + u64be(payload.size.toLong()) + payload + pad
    }

    private fun u32le(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    private fun u64le(value: Long): ByteArray =
        u32le(value.toInt()) + u32le((value ushr 32).toInt())

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

    private fun u64be(value: Long): ByteArray =
        u32be((value ushr 32).toInt()) + u32be(value.toInt())
}
