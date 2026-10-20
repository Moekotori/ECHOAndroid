package app.echo.android.data

import java.io.InputStream
import java.io.OutputStream

/** Keep only the last 128 bytes pending; the audio body is always streamed. */
internal fun copyMp3WithUpdatedLegacyTags(input: InputStream, output: OutputStream, fields: AudioTagFields) {
    val buffer = ByteArray(64 * 1024 + 128)
    var pending = 0
    while (true) {
        val read = input.read(buffer, pending, buffer.size - pending)
        if (read < 0) break
        if (read == 0) continue
        val total = pending + read
        val flush = (total - 128).coerceAtLeast(0)
        output.write(buffer, 0, flush)
        pending = total - flush
        buffer.copyInto(buffer, 0, flush, total)
    }
    if (pending == 128 && buffer[0] == 'T'.code.toByte() &&
        buffer[1] == 'A'.code.toByte() && buffer[2] == 'G'.code.toByte()
    ) {
        fun text(offset: Int, length: Int, value: String?) {
            buffer.fill(0, offset, offset + length)
            // ID3v2 holds Unicode; leave v1 empty when its charset cannot represent the value.
            if (value != null && Charsets.ISO_8859_1.newEncoder().canEncode(value)) {
                val bytes = value.toByteArray(Charsets.ISO_8859_1)
                bytes.copyInto(buffer, offset, 0, minOf(length, bytes.size))
            }
        }
        text(3, 30, fields.title)
        text(33, 30, fields.artist)
        text(63, 30, fields.album)
        text(93, 4, fields.year?.toString())
        if (buffer[125] == 0.toByte()) {
            buffer[126] = (fields.trackNumber?.takeIf { it in 1..255 } ?: 0).toByte()
        }
        // Preserve the legacy comment and genre, including v1.0's full 30-byte comment.
    }
    output.write(buffer, 0, pending)
}
