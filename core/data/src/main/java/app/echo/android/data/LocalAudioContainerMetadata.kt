package app.echo.android.data

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.util.Locale
import kotlin.math.pow

internal data class LocalAudioContainerInfo(val durationMs: Long, val sampleRateHz: Int?, val tags: AudioTagFields? = null)

/** Bounded header reads, independent of the device's MediaMetadataRetriever codecs. */
internal object LocalAudioContainerMetadata {
    fun supports(mime: String?, name: String?): Boolean {
        val type = mime.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
        val extension = name.orEmpty().substringBefore('?').substringAfterLast('.').lowercase(Locale.ROOT)
        return LibraryWavTagPolicy.isWavContainer(mime, name) || EchoApeMetadata.isApe(mime, name) ||
            isFlac(mime, name) || extension in setOf("wv", "aif", "aiff", "aifc", "wma", "asf", "tta", "tak", "w64") ||
            type in setOf("audio/x-wavpack", "audio/wavpack", "audio/aiff", "audio/x-aiff", "audio/x-ms-wma", "audio/x-ms-asf",
                "audio/x-tta", "audio/tta", "audio/x-tak", "audio/tak", "audio/x-w64", "audio/w64")
    }

    fun isFlac(mime: String?, name: String?): Boolean =
        mime.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT) in
            setOf("audio/flac", "audio/x-flac", "application/flac", "application/x-flac") ||
            name.orEmpty().substringBefore('?').substringAfterLast('.').lowercase(Locale.ROOT) in setOf("flac", "fla")

    fun read(input: InputStream): LocalAudioContainerInfo? {
        val header = input.tagBytes(12) ?: return null
        val id = header.decodeToString(0, 4)
        return when {
            id in listOf("RIFF", "RF64") && header.decodeToString(8, 12) == "WAVE" -> wave(header, input)
            id == "FORM" && header.decodeToString(8, 12) in listOf("AIFF", "AIFC") -> aiff(header, input)
            id == "wvpk" -> wavPack(header + (input.tagBytes(20) ?: return null), input)
            id == "fLaC" -> {
                if (header[4].toInt() and 127 != 0 || header[5].toInt() != 0 ||
                    header[6].toInt() != 0 || header[7].toInt() != 34) return null
                val stream = header + (input.tagBytes(30) ?: return null)
                val packed = (18..25).fold(0L) { value, index -> (value shl 8) or (stream[index].toLong() and 255) }
                val rate = (packed ushr 44).toInt()
                if (rate !in 1..768000) return null
                LocalAudioContainerInfo((packed and 0xfffffffffL) * 1000 / rate, rate)
            }
            id in listOf("TTA1", "tBaK", "riff") || id.startsWith("ID3") ->
                AdditionalLosslessMetadata.read(SequenceInputStream(ByteArrayInputStream(header), input))
            id == "MAC " -> EchoApeMetadata.read(SequenceInputStream(ByteArrayInputStream(header), input))
                ?.let { LocalAudioContainerInfo(it.durationMs, it.sampleRateHz) }
            header.copyOfRange(0, 12).contentEquals(AsfHeader.copyOfRange(0, 12)) ->
                AsfMetadataReader.read(SequenceInputStream(ByteArrayInputStream(header), input))
            else -> null
        }
    }

    private fun wave(header: ByteArray, input: InputStream): LocalAudioContainerInfo? {
        var remaining = if (header.decodeToString(0, 4) == "RF64") Long.MAX_VALUE else le32(header, 4) - 4
        var rate: Int? = null
        var byteRate = 0L
        var format = 0
        var samples = 0L
        var data64: Long? = null
        var scanned = 0L
        repeat(256) {
            if (remaining < 8 || scanned > HeaderBudget) return null
            val chunk = input.tagBytes(8) ?: return null
            val id = chunk.decodeToString(0, 4)
            val size = le32(chunk, 4)
            remaining -= 8
            if (id == "data") {
                val dataSize = if (size == 0xffffffffL) data64 ?: return null else size
                if (dataSize < 0 || rate == null) return null
                val duration = if (format in listOf(1, 3, 0xfffe) && byteRate > 0) {
                    val seconds = dataSize / byteRate
                    if (seconds > Long.MAX_VALUE / 1000) return null
                    val whole = seconds * 1000
                    val fraction = dataSize % byteRate * 1000 / byteRate
                    if (fraction > Long.MAX_VALUE - whole) return null
                    whole + fraction
                } else if (samples > 0) samples * 1000 / rate else 0
                return LocalAudioContainerInfo(duration, rate)
            }
            if (size > remaining || size > HeaderBudget - scanned) return null
            when (id) {
                "fmt " -> {
                    if (size < 16) return null
                    val fmt = input.tagBytes(minOf(size, 40).toInt()) ?: return null
                    format = le16(fmt, 0)
                    rate = le32(fmt, 4).toInt().takeIf { it in 1..768000 }
                    byteRate = le32(fmt, 8)
                    if (format == 0xfffe) {
                        if (fmt.size < 40 || le16(fmt, 16) < 22) return null
                        // Only PCM/IEEE float extensible subformats have a constant byte rate.
                        format = le16(fmt, 24)
                    }
                    if (!input.skipTagBytes(size - fmt.size)) return null
                }
                "ds64" -> {
                    if (size < 28) return null
                    val ds = input.tagBytes(28) ?: return null
                    data64 = le64(ds, 8).takeIf { it >= 0 }
                    if (!input.skipTagBytes(size - 28)) return null
                }
                "fact" -> {
                    if (size < 4) return null
                    samples = le32(input.tagBytes(4) ?: return null, 0)
                    if (!input.skipTagBytes(size - 4)) return null
                }
                else -> if (!input.skipTagBytes(size)) return null
            }
            if (!input.skipTagBytes(size and 1)) return null
            remaining -= size + (size and 1)
            scanned += 8 + size + (size and 1)
        }
        return null
    }

    private fun aiff(header: ByteArray, input: InputStream): LocalAudioContainerInfo? {
        var remaining = header.tagU32(4) - 4
        var scanned = 0L
        repeat(256) {
            if (remaining < 8 || scanned > HeaderBudget) return null
            val chunk = input.tagBytes(8) ?: return null
            val size = chunk.tagU32(4)
            remaining -= 8
            if (size > remaining || size > HeaderBudget - scanned) return null
            if (chunk.decodeToString(0, 4) == "COMM") {
                if (size < if (header.decodeToString(8, 12) == "AIFC") 22 else 18) return null
                val comm = input.tagBytes(18) ?: return null
                val exponent = ((comm[8].toInt() and 127) shl 8) or (comm[9].toInt() and 255)
                if (comm[8].toInt() and 128 != 0 || exponent == 0 || exponent == 0x7fff) return null
                val mantissa = (0..7).fold(0.0) { value, i -> value * 256 + (comm[10 + i].toInt() and 255) }
                val rate = mantissa * 2.0.pow(exponent - 16383 - 63)
                if (!rate.isFinite() || rate !in 1.0..768000.0 || comm.tagU32(2) == 0L) return null
                return LocalAudioContainerInfo((comm.tagU32(2) * 1000 / rate).toLong(), rate.toInt())
            }
            if (!input.skipTagBytes(size + (size and 1))) return null
            remaining -= size + (size and 1)
            scanned += 8 + size + (size and 1)
        }
        return null
    }

    private fun wavPack(first: ByteArray, input: InputStream): LocalAudioContainerInfo? {
        var header = first
        var scanned = 0L
        repeat(32) {
            val size = le32(header, 4) - 24
            if (size !in 0..HeaderBudget || le16(header, 8) !in 0x402..0x410) return null
            val flags = le32(header, 24)
            if (le32(header, 20) > 0) {
                // WavPack DSD has additional rate semantics; leave it to the backend.
                if (flags and 0x80000000L != 0L) return null
                var rate = Rates[((flags shr 23) and 15).toInt()]
                if (rate == 0) {
                    val data = input.tagBytes(size.toInt()) ?: return null
                    rate = customWavPackRate(data) ?: return null
                }
                val low = le32(header, 12)
                val total = low + (header[11].toLong() and 255) * 0xffffffffL
                val index = le32(header, 16) or ((header[10].toLong() and 255) shl 32)
                return LocalAudioContainerInfo(if (low == 0xffffffffL || index != 0L) 0 else total * 1000 / rate, rate)
            }
            scanned += size + 32
            if (scanned > HeaderBudget || !input.skipTagBytes(size)) return null
            header = input.tagBytes(32) ?: return null
            if (header.decodeToString(0, 4) != "wvpk") return null
        }
        return null
    }

    private fun customWavPackRate(data: ByteArray): Int? {
        var at = 0
        while (at + 2 <= data.size) {
            val id = data[at++].toInt() and 255
            var length = data[at++].toInt() and 255
            if (id and 128 != 0) {
                if (at + 2 > data.size) return null
                length += (data[at++].toInt() and 255) shl 8
                length += (data[at++].toInt() and 255) shl 16
            }
            length *= 2
            if (length > data.size - at) return null
            val actual = length - if (id and 64 != 0) 1 else 0
            if (id and 63 == 0x27 && actual == 3) {
                return ((data[at].toInt() and 255) or ((data[at + 1].toInt() and 255) shl 8) or
                    ((data[at + 2].toInt() and 255) shl 16)).takeIf { it in 1..768000 }
            }
            at += length
        }
        return null
    }

    internal fun le16(b: ByteArray, at: Int): Int = (b[at].toInt() and 255) or ((b[at + 1].toInt() and 255) shl 8)
    internal fun le32(b: ByteArray, at: Int): Long = (0..3).fold(0L) { v, i -> v or ((b[at + i].toLong() and 255) shl (8 * i)) }
    internal fun le64(b: ByteArray, at: Int): Long = le32(b, at) or (le32(b, at + 4) shl 32)
    private const val HeaderBudget = 2L * 1024 * 1024
    private val Rates = intArrayOf(6000, 8000, 9600, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000, 64000, 88200, 96000, 192000, 0)
    internal val AsfHeader = byteArrayOf(0x30, 0x26, -0x4e, 0x75, -0x72, 0x66, -0x31, 0x11, -0x5a, -0x27, 0, -0x56, 0, 0x62, -0x32, 0x6c)
}
