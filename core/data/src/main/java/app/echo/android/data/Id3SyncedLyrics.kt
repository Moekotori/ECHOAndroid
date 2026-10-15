package app.echo.android.data

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

internal fun decodeSyncedId3Lyrics(payload: ByteArray): String? {
    if (payload.size <= 7) return null
    val encoding = id3Encoding(payload[0])
    val timestampFormat = payload[4].toInt() and 0xFF
    if (timestampFormat != 2) return null
    var cursor = findTerminator(payload, start = 6, encoding = encoding)?.let { it + encoding.terminatorSize }
        ?: return null
    val lines = mutableListOf<String>()
    while (cursor < payload.size) {
        val end = findTerminator(payload, start = cursor, encoding = encoding) ?: break
        val text = decodeId3Text(payload, cursor, end, encoding).trim()
        cursor = end + encoding.terminatorSize
        if (cursor + 4 > payload.size) break
        val timeMs = int32(payload, cursor)
        cursor += 4
        if (text.isNotBlank()) lines += "[${formatLrcTime(timeMs)}]$text"
    }
    return lines.takeIf { it.isNotEmpty() }
        ?.joinToString("\n")
}

private fun id3Encoding(value: Byte): Id3TextEncoding =
    when (value.toInt() and 0xFF) {
        1 -> Id3TextEncoding(Charsets.UTF_16, 2)
        2 -> Id3TextEncoding(Charsets.UTF_16BE, 2)
        3 -> Id3TextEncoding(StandardCharsets.UTF_8, 1)
        else -> Id3TextEncoding(StandardCharsets.ISO_8859_1, 1)
    }

private fun decodeId3Text(payload: ByteArray, start: Int, end: Int, encoding: Id3TextEncoding): String {
    if (start >= end || start >= payload.size) return ""
    val slice = payload.copyOfRange(start, end.coerceAtMost(payload.size))
    val text = if (encoding.charset == StandardCharsets.ISO_8859_1) {
        decodeLegacyTagText(slice).orEmpty()
    } else {
        slice.toString(encoding.charset)
    }
    return text.trimEnd('\u0000')
}

private fun findTerminator(payload: ByteArray, start: Int, encoding: Id3TextEncoding): Int? {
    var index = start
    while (index < payload.size) {
        if (encoding.terminatorSize == 1) {
            if (payload[index] == 0.toByte()) return index
            index += 1
        } else {
            if (index + 1 < payload.size && payload[index] == 0.toByte() && payload[index + 1] == 0.toByte()) {
                return index
            }
            index += 2
        }
    }
    return null
}

private fun int32(bytes: ByteArray, start: Int): Int =
    ((bytes[start].toInt() and 0xFF) shl 24) or
        ((bytes[start + 1].toInt() and 0xFF) shl 16) or
        ((bytes[start + 2].toInt() and 0xFF) shl 8) or
        (bytes[start + 3].toInt() and 0xFF)

private fun formatLrcTime(timeMs: Int): String {
    val safeMs = timeMs.coerceAtLeast(0)
    val minutes = safeMs / 60_000
    val seconds = (safeMs % 60_000) / 1_000
    val hundredths = (safeMs % 1_000) / 10
    return "%02d:%02d.%02d".format(java.util.Locale.ROOT, minutes, seconds, hundredths)
}

private data class Id3TextEncoding(
    val charset: Charset,
    val terminatorSize: Int,
)

