package app.echo.android.data

import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.Charset

/** Only legacy ID3 text is overridden. Declared UTF encodings keep their declared meaning. */
internal object LegacyMetadataPreviewReader {
    fun read(input: FileInputStream, charset: Charset): Map<String, String> {
        val header = input.tagBytes(10) ?: return emptyMap()
        if (header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) || header.copyOfRange(0, 4).contentEquals("RF64".toByteArray())) {
            return readWaveInfo(input, header + (input.tagBytes(2) ?: return emptyMap()), charset)
        }
        val result = LinkedHashMap<String, String>()
        var legacy = false
        if (header.copyOfRange(0, 3).contentEquals("ID3".toByteArray())) {
            val tag = Id3TagReader.read(header, input) ?: return emptyMap()
            for (frame in tag.frames) {
                if (frame.id !in setOf("TIT2", "TPE1", "TALB", "TPE2")) continue
                val payload = AudioFileTagRewriter.readableId3Payload(frame, tag.version) ?: continue
                if (payload.isEmpty()) continue
                val bytes = payload.copyOfRange(1, payload.size)
                val decoded = when (payload[0].toInt()) {
                    0 -> TagTextDecoder.decodeDeclared(bytes, charset)
                    1 -> TagTextDecoder.decodeDeclared(bytes, Charsets.UTF_16)
                    2 -> TagTextDecoder.decodeDeclared(bytes, Charsets.UTF_16BE)
                    3 -> TagTextDecoder.decodeDeclared(bytes, Charsets.UTF_8)
                    else -> null
                }
                decoded?.takeIf { it.isNotBlank() }?.let {
                    result[frame.id] = it.take(1024)
                    if (payload[0] == 0.toByte()) legacy = true
                }
            }
        }
        val tail = readTagTail(input, 128)
        if (tail != null && tail.take(3) == "TAG".toByteArray().toList()) {
            listOf("TIT2" to (3..32), "TPE1" to (33..62), "TALB" to (63..92)).forEach { (key, range) ->
                if (key !in result) TagTextDecoder.decodeDeclared(tail.copyOfRange(range.first, range.last + 1), charset)
                    ?.takeIf { it.isNotBlank() }?.let { result[key] = it; legacy = true }
            }
        }
        return if (legacy) result else emptyMap()
    }

    private fun readWaveInfo(input: InputStream, header: ByteArray, charset: Charset): Map<String, String> {
        if (!header.copyOfRange(8, 12).contentEquals("WAVE".toByteArray())) return emptyMap()
        fun length(bytes: ByteArray, offset: Int) = (bytes[offset].toLong() and 255) or
            ((bytes[offset + 1].toLong() and 255) shl 8) or ((bytes[offset + 2].toLong() and 255) shl 16) or
            ((bytes[offset + 3].toLong() and 255) shl 24)
        val fields = LinkedHashMap<String, String>()
        repeat(512) {
            val chunk = input.tagBytes(8) ?: return fields
            val size = length(chunk, 4)
            if (size == 0xffffffffL) return fields // RF64 extended sizes need the normal parser.
            if (chunk.copyOfRange(0, 4).contentEquals("LIST".toByteArray()) && size >= 4) {
                val slice = TagSliceInput(input, size)
                val info = slice.tagBytes(4) ?: return fields
                if (info.contentEquals("INFO".toByteArray())) for (itemIndex in 0 until 128) {
                    if (slice.remaining < 8) break
                    val item = slice.tagBytes(8) ?: break
                    val count = length(item, 4)
                    if (count > slice.remaining) return fields
                    val key = when (item.copyOfRange(0, 4).toString(Charsets.US_ASCII)) {
                        "INAM" -> "TIT2"; "IART" -> "TPE1"; "IPRD" -> "TALB"; else -> null
                    }
                    if (key != null && count <= 16384) {
                        slice.tagBytes(count.toInt())?.let { TagTextDecoder.decodeDeclared(it, charset) }?.takeIf { it.isNotBlank() }
                            ?.let { fields[key] = it.take(1024) }
                    } else slice.skipTagBytes(count)
                    if (count and 1L != 0L) slice.skipTagBytes(1)
                }
                slice.skipTagBytes(slice.remaining)
                if (size and 1L != 0L) input.skipTagBytes(1)
            } else if (!input.skipTagBytes(size + (size and 1L))) return fields
        }
        return fields
    }
}
