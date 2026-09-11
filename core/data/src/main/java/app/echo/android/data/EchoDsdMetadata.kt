package app.echo.android.data

import app.echo.android.model.library.LibraryPlaybackSupport
import java.io.ByteArrayInputStream
import java.io.InputStream

internal data class EchoDsdScanInfo(
    val durationMs: Long,
    val sampleRateHz: Int,
    val tags: AudioTagFields? = null,
    val compressed: Boolean = false,
)

internal object EchoDsdMetadata {
    fun isDsd(mimeType: String?, uriOrName: String?): Boolean =
        LibraryPlaybackSupport.isDsd(mimeType, uriOrName)

    fun read(input: InputStream): EchoDsdScanInfo? {
        val header = ByteArray(92)
        val read = readFully(input, header)
        if (read < 16) return null
        if (isDsf(header)) {
            if (read < 92) return null
            return readDsf(header, input)
        }
        if (isDff(header)) {
            return readDff(PrefixBytes(header, read, input))
        }
        return null
    }

    private fun isDsf(header: ByteArray): Boolean =
        fourcc(header, 0) == "DSD " && u64le(header, 4) == 28L

    private fun isDff(header: ByteArray): Boolean =
        fourcc(header, 0) == "FRM8" && header.size >= 16 && fourcc(header, 12) == "DSD "

    private fun readDsf(header: ByteArray, rest: InputStream): EchoDsdScanInfo? {
        if (fourcc(header, 28) != "fmt " || u64le(header, 32) != 52L) return null
        if (u32le(header, 40) != 1L || u32le(header, 44) != 0L) return null
        val channels = u32le(header, 52).toInt()
        if (channels !in 1..6) return null
        val dsdRateHz = u32le(header, 56).toInt()
        if (dsdRateHz < 8 || dsdRateHz % 8 != 0) return null
        val bits = u32le(header, 60).toInt()
        if (bits != 1 && bits != 8) return null
        val sampleCount = u64le(header, 64)
        if (sampleCount <= 0L) return null
        val durationMs = (sampleCount * 1000L) / dsdRateHz
        val id3Offset = u64le(header, 20)
        var tags: AudioTagFields? = null
        if (id3Offset >= 92L) {
            if (skipExact(rest, id3Offset - 92L)) {
                tags = readLocalAudioTags(rest)
            }
        }
        return EchoDsdScanInfo(durationMs = durationMs, sampleRateHz = dsdRateHz, tags = tags)
    }

    private fun readDff(input: InputStream): EchoDsdScanInfo? {
        skipExact(input, 16L)
        var channels = 0
        var dsdRateHz = 0
        var dataSize = -1L
        var compressed = false
        var tags: AudioTagFields? = null
        var chunks = 0
        while (chunks < 64) {
            chunks++
            val chunk = input.readExact(12) ?: break
            val tag = fourcc(chunk, 0)
            val size = u64be(chunk, 4)
            if (size < 0L || size > 1L shl 40) return null
            val padded = size + (size and 1L)
            when (tag) {
                "PROP" -> {
                    val payload = input.readExact(size.toInt().coerceAtMost(1_048_576)) ?: return null
                    if (padded > size && !skipExact(input, padded - size)) return null
                    parseDffProp(payload)?.let { prop ->
                        if (prop.channels > 0) channels = prop.channels
                        if (prop.dsdRateHz > 0) dsdRateHz = prop.dsdRateHz
                        compressed = compressed || prop.compressed
                        tags = mergeAudioTags(preferred = tags, fallback = prop.tags)
                    }
                }
                "DSD " -> {
                    dataSize = size
                    break
                }
                "DST " -> {
                    compressed = true
                    dataSize = size
                    break
                }
                "ID3 " -> {
                    if (size in 10L..1_048_576L) {
                        val payload = input.readExact(size.toInt()) ?: return null
                        tags = mergeAudioTags(
                            preferred = tags,
                            fallback = readLocalAudioTags(ByteArrayInputStream(payload)),
                        )
                        if (padded > size && !skipExact(input, padded - size)) return null
                    } else if (!skipExact(input, padded)) {
                        return null
                    }
                }
                else -> if (!skipExact(input, padded)) return null
            }
        }
        if (channels !in 1..6 || dsdRateHz < 8 || dsdRateHz % 8 != 0 || dataSize <= 0L) return null
        val durationMs = ((dataSize / channels) * 8000L) / dsdRateHz
        return EchoDsdScanInfo(
            durationMs = durationMs,
            sampleRateHz = dsdRateHz,
            tags = tags,
            compressed = compressed,
        )
    }

    private data class DffProp(
        val channels: Int,
        val dsdRateHz: Int,
        val compressed: Boolean,
        val tags: AudioTagFields?,
    )

    private fun parseDffProp(payload: ByteArray): DffProp? {
        if (payload.size < 4 || fourcc(payload, 0) != "SND ") return null
        var offset = 4
        var channels = 0
        var rate = 0
        var compressed = false
        var tags: AudioTagFields? = null
        while (offset + 12 <= payload.size) {
            val tag = fourcc(payload, offset)
            val size = u64be(payload, offset + 4)
            offset += 12
            if (size < 0L || offset + size > payload.size) break
            val start = offset
            when (tag) {
                "FS  " -> if (size >= 4L) rate = u32be(payload, start).toInt()
                "CHNL" -> if (size >= 2L) channels = u16be(payload, start)
                "CMPR" -> if (size >= 4L) compressed = fourcc(payload, start) == "DST "
                "ID3 " -> if (size in 10L..payload.size.toLong()) {
                    tags = readLocalAudioTags(
                        ByteArrayInputStream(payload.copyOfRange(start, start + size.toInt())),
                    )
                }
            }
            offset += size.toInt()
            if ((size and 1L) == 1L) offset++
        }
        return DffProp(channels, rate, compressed, tags)
    }

    private class PrefixBytes(
        private val prefix: ByteArray,
        private val prefixLength: Int,
        private val rest: InputStream,
    ) : InputStream() {
        private var prefixPos = 0

        override fun read(): Int {
            val one = ByteArray(1)
            val n = read(one, 0, 1)
            return if (n <= 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len <= 0) return 0
            if (prefixPos < prefixLength) {
                val n = minOf(len, prefixLength - prefixPos)
                System.arraycopy(prefix, prefixPos, b, off, n)
                prefixPos += n
                return n
            }
            return rest.read(b, off, len)
        }

        override fun skip(n: Long): Long {
            if (n <= 0L) return 0L
            var left = n
            if (prefixPos < prefixLength) {
                val fromPrefix = minOf(left, (prefixLength - prefixPos).toLong())
                prefixPos += fromPrefix.toInt()
                left -= fromPrefix
            }
            if (left > 0L) left -= rest.skip(left)
            return n - left
        }
    }

    private fun fourcc(bytes: ByteArray, start: Int): String {
        if (start + 4 > bytes.size) return ""
        return String(bytes, start, 4, Charsets.ISO_8859_1)
    }

    private fun u16be(bytes: ByteArray, start: Int): Int =
        ((bytes[start].toInt() and 0xFF) shl 8) or (bytes[start + 1].toInt() and 0xFF)

    private fun u32be(bytes: ByteArray, start: Int): Long =
        ((bytes[start].toLong() and 0xFFL) shl 24) or
            ((bytes[start + 1].toLong() and 0xFFL) shl 16) or
            ((bytes[start + 2].toLong() and 0xFFL) shl 8) or
            (bytes[start + 3].toLong() and 0xFFL)

    private fun u32le(bytes: ByteArray, start: Int): Long =
        (bytes[start].toLong() and 0xFFL) or
            ((bytes[start + 1].toLong() and 0xFFL) shl 8) or
            ((bytes[start + 2].toLong() and 0xFFL) shl 16) or
            ((bytes[start + 3].toLong() and 0xFFL) shl 24)

    private fun u64le(bytes: ByteArray, start: Int): Long =
        u32le(bytes, start) or (u32le(bytes, start + 4) shl 32)

    private fun u64be(bytes: ByteArray, start: Int): Long =
        (u32be(bytes, start) shl 32) or u32be(bytes, start + 4)

    private fun readFully(input: InputStream, dst: ByteArray): Int {
        var offset = 0
        while (offset < dst.size) {
            val n = input.read(dst, offset, dst.size - offset)
            if (n < 0) return offset
            offset += n
        }
        return offset
    }

    private fun skipExact(input: InputStream, count: Long): Boolean {
        if (count <= 0L) return true
        var left = count
        val buffer = ByteArray(4096)
        while (left > 0L) {
            var skipped = input.skip(left)
            if (skipped <= 0L) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (read < 0) return false
                skipped = read.toLong()
            }
            left -= skipped
        }
        return true
    }

    private fun InputStream.readExact(size: Int): ByteArray? {
        if (size < 0) return null
        if (size == 0) return ByteArray(0)
        val out = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val n = read(out, offset, size - offset)
            if (n < 0) return null
            offset += n
        }
        return out
    }
}
