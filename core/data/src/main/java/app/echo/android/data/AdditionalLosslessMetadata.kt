package app.echo.android.data

import app.echo.android.data.LocalAudioContainerMetadata.le16
import app.echo.android.data.LocalAudioContainerMetadata.le32
import app.echo.android.data.LocalAudioContainerMetadata.le64
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.util.zip.CRC32

/** TTA/TAK/Wave64 header facts without decoding or walking the audio payload. */
internal object AdditionalLosslessMetadata {
    fun read(input: InputStream): LocalAudioContainerInfo? = read(input, allowId3 = true)

    private fun read(input: InputStream, allowId3: Boolean): LocalAudioContainerInfo? {
        val magic = input.tagBytes(4) ?: return null
        val id = magic.decodeToString()
        if (id.startsWith("ID3") && allowId3) {
            val header = magic + (input.tagBytes(6) ?: return null)
            val size = header.tagSyncSafe(6) ?: return null
            if (size > MaxEmbeddedTextBytes || !input.skipTagBytes(size.toLong() +
                if (header[3].toInt() == 4 && header[5].toInt() and 16 != 0) 10 else 0)) return null
            return read(input, allowId3 = false)
        }
        return when (id) {
            "TTA1" -> {
                val header = magic + (input.tagBytes(18) ?: return null)
                val rate = le32(header, 10)
                val samples = le32(header, 14)
                val crc = CRC32().apply { update(header, 0, 18) }.value
                // Password-protected TTA has no password contract in the player.
                if (le16(header, 4) != 1 || le16(header, 6) !in 1..8 || le16(header, 8) !in 1..32 ||
                    rate !in 1..768000 || samples == 0L || crc != le32(header, 18)) return null
                LocalAudioContainerInfo(samples * 1000 / rate, rate.toInt())
            }
            "tBaK" -> tak(input)
            "riff" -> wave64(magic, input)
            "MAC " -> EchoApeMetadata.read(SequenceInputStream(ByteArrayInputStream(magic), input))
                ?.let { LocalAudioContainerInfo(it.durationMs, it.sampleRateHz) }
            else -> null
        }
    }

    private fun tak(input: InputStream): LocalAudioContainerInfo? {
        var remaining = MaxEmbeddedTextBytes
        repeat(128) {
            val header = input.tagBytes(4) ?: return null
            val type = header[0].toInt() and 127
            val size = (1..3).fold(0) { v, i -> v or ((header[i].toInt() and 255) shl ((i - 1) * 8)) }
            if (type == 0 || size > remaining - 4) return null
            remaining -= size + 4
            if (type == 1) {
                if (size !in 13..1024) return null
                val info = input.tagBytes(size) ?: return null
                fun bits(at: Int, count: Int): Long = (0 until count).fold(0L) { value, i ->
                    value or (((info[(at + i) / 8].toLong() shr ((at + i) % 8)) and 1) shl i)
                }
                val codec = bits(0, 6)
                val frameType = bits(10, 4)
                val samples = bits(14, 35)
                val dataType = bits(49, 3)
                val rate = bits(52, 18) + 6000
                val bps = bits(70, 5) + 8
                val channels = bits(75, 4) + 1
                if (codec !in listOf(2L, 4L) || frameType > 9 || dataType != 0L ||
                    samples == 0L || rate > 768000 || bps > 32 || channels > 8) return null
                return LocalAudioContainerInfo(samples * 1000 / rate, rate.toInt())
            }
            if (!input.skipTagBytes(size.toLong())) return null
        }
        return null
    }

    private fun wave64(magic: ByteArray, input: InputStream): LocalAudioContainerInfo? {
        val header = magic + (input.tagBytes(36) ?: return null)
        if (!header.copyOfRange(0, 16).contentEquals(RiffGuid) ||
            !header.copyOfRange(24, 40).contentEquals(guid("wave"))) return null
        val fileSize = le64(header, 16)
        if (fileSize < 64) return null
        var remaining = fileSize - 40
        var budget = MaxEmbeddedTextBytes.toLong()
        var rate = 0L
        var byteRate = 0L
        var format = 0
        var samples = 0L
        repeat(256) {
            if (remaining < 24 || budget < 24) return null
            val chunk = input.tagBytes(24) ?: return null
            val size = le64(chunk, 16)
            if (size < 24 || size > remaining) return null
            val id = chunk.copyOfRange(0, 16)
            val payload = size - 24
            if (id.contentEquals(guid("data"))) {
                if (rate <= 0) return null
                val duration = if (samples > 0 && samples <= Long.MAX_VALUE / 1000) samples * 1000 / rate
                    else if (format in listOf(1, 3) && byteRate > 0 && payload <= Long.MAX_VALUE / 1000) payload * 1000 / byteRate
                    else 0
                return LocalAudioContainerInfo(duration, rate.toInt())
            }
            if (size > budget) return null
            when {
                id.contentEquals(guid("fmt ")) -> {
                    if (payload < 16) return null
                    val fmt = input.tagBytes(minOf(payload, 40).toInt()) ?: return null
                    format = le16(fmt, 0)
                    rate = le32(fmt, 4)
                    byteRate = le32(fmt, 8)
                    if (rate !in 1..768000 || le16(fmt, 2) !in 1..8) return null
                    if (format == 0xfffe) {
                        if (fmt.size < 40 || le16(fmt, 16) < 22) return null
                        format = le16(fmt, 24)
                    }
                    if (!input.skipTagBytes(payload - fmt.size)) return null
                }
                id.contentEquals(guid("fact")) -> {
                    if (payload < 8) return null
                    samples = le64(input.tagBytes(8) ?: return null, 0)
                    if (!input.skipTagBytes(payload - 8)) return null
                }
                else -> if (!input.skipTagBytes(payload)) return null
            }
            val padding = (8 - (size and 7)) and 7
            if (padding > remaining - size || !input.skipTagBytes(padding)) return null
            remaining -= size + padding
            budget -= size + padding
        }
        return null
    }

    private val RiffGuid = byteArrayOf(0x72, 0x69, 0x66, 0x66, 0x2e, -0x6f, -0x31, 0x11,
        -0x5b, -0x2a, 0x28, -0x25, 4, -0x3f, 0, 0)
    private val ChunkGuidSuffix = byteArrayOf(-0x0d, -0x54, -0x2d, 0x11, -0x74, -0x2f, 0,
        -0x40, 0x4f, -0x72, -0x25, -0x76)
    private fun guid(id: String): ByteArray = id.toByteArray(Charsets.US_ASCII) + ChunkGuidSuffix
}
