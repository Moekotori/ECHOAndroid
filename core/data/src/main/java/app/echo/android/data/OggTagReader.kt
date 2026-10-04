package app.echo.android.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Assemble only the first two header packets of one audio stream, including page continuations. */
internal object OggTagReader {
    fun read(input: InputStream): AudioTagFields? {
        var serial: Long? = null
        var sequence: Long? = null
        var codec: String? = null
        var packetNumber = 0
        var bytesRead = 0
        val packet = ByteArrayOutputStream()
        repeat(256) {
            val header = input.tagBytes(27) ?: return null
            if (header.copyOfRange(0, 4).toString(Charsets.US_ASCII) != "OggS" || header[4] != 0.toByte()) return null
            fun le(offset: Int): Long = (0..3).fold(0L) { value, index ->
                value or ((header[offset + index].toLong() and 255) shl (index * 8))
            }
            val pageSerial = le(14)
            val pageSequence = le(18)
            val laces = input.tagBytes(header[26].toInt() and 255) ?: return null
            val pageSize = laces.sumOf { it.toInt() and 255 }
            bytesRead += 27 + laces.size + pageSize
            if (bytesRead > MaxHeaderBytes) return null
            if (serial != null && pageSerial != serial) {
                if (!input.skipTagBytes(pageSize.toLong())) return null
                return@repeat
            }
            if (serial == null) serial = pageSerial
            if (sequence != null && pageSequence != ((sequence!! + 1) and 0xFFFFFFFFL)) return null
            sequence = pageSequence
            val continued = header[5].toInt() and 1 != 0
            if (continued != (packet.size() > 0)) return null
            for (lace in laces) {
                val size = lace.toInt() and 255
                if (packet.size() + size > MaxHeaderBytes) return null
                packet.write(input.tagBytes(size) ?: return null)
                if (size < 255) {
                    val bytes = packet.toByteArray()
                    if (packetNumber == 0) {
                        codec = when {
                            bytes.size >= 8 && bytes.copyOfRange(0, 8).toString(Charsets.US_ASCII) == "OpusHead" -> "opus"
                            bytes.size >= 7 && bytes[0] == 1.toByte() && bytes.copyOfRange(1, 7).toString(Charsets.US_ASCII) == "vorbis" -> "vorbis"
                            else -> return null
                        }
                    } else {
                        val prefix = if (codec == "opus") 8 else 7
                        val valid = if (codec == "opus") bytes.size >= 8 && bytes.copyOfRange(0, 8).toString(Charsets.US_ASCII) == "OpusTags"
                            else bytes.size >= 7 && bytes[0] == 3.toByte() && bytes.copyOfRange(1, 7).toString(Charsets.US_ASCII) == "vorbis"
                        if (!valid) return null
                        return VorbisTagReader.fields(bytes.copyOfRange(prefix, bytes.size)).takeUnless { it.isBlank() }
                    }
                    packetNumber++
                    packet.reset()
                }
            }
        }
        return null
    }
    private const val MaxHeaderBytes = 12 * 1024 * 1024
}
