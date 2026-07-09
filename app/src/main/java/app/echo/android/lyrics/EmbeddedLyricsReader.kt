package app.echo.android.lyrics

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

internal object EmbeddedLyricsReader {
    fun read(input: InputStream): EmbeddedLyricsText? {
        val header = ByteArray(10)
        val read = input.read(header)
        if (read < header.size) return null

        return when {
            header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte() ->
                readId3v2(input, header)

            header[0] == 'f'.code.toByte() &&
                header[1] == 'L'.code.toByte() &&
                header[2] == 'a'.code.toByte() &&
                header[3] == 'C'.code.toByte() ->
                readFlac(input)

            else -> null
        }
    }

    private fun readId3v2(input: InputStream, header: ByteArray): EmbeddedLyricsText? {
        val majorVersion = header[3].toInt()
        if (majorVersion !in 2..4) return null
        val tagSize = syncSafeInt(header, 6).takeIf { it in 1..MAX_TAG_BYTES } ?: return null
        val tagBytes = input.readExactly(tagSize) ?: return null
        val frames = ByteArrayInputStream(tagBytes)

        while (frames.available() >= 10) {
            val frameHeader = frames.readExactly(10) ?: return null
            val frameId = frameHeader.copyOfRange(0, 4).toString(StandardCharsets.ISO_8859_1)
            if (frameId.all { it.code == 0 }) return null
            val frameSize = if (majorVersion == 4) {
                syncSafeInt(frameHeader, 4)
            } else {
                int32(frameHeader, 4)
            }
            if (frameSize <= 0 || frameSize > frames.available()) return null
            val payload = frames.readExactly(frameSize) ?: return null
            val lyrics = when (frameId) {
                "USLT" -> parseUnsyncedId3Lyrics(payload)
                "SYLT" -> parseSyncedId3Lyrics(payload)
                else -> null
            }
            if (lyrics?.text?.isNotBlank() == true) return lyrics
        }
        return null
    }

    private fun parseUnsyncedId3Lyrics(payload: ByteArray): EmbeddedLyricsText? {
        if (payload.size <= 5) return null
        val encoding = id3Encoding(payload[0])
        val textStart = findTerminator(payload, start = 4, encoding = encoding)?.let { it + encoding.terminatorSize }
            ?: 4
        val text = decodeId3Text(payload, textStart, payload.size, encoding)
        return text.takeIf { it.isNotBlank() }?.let { EmbeddedLyricsText(it, "Embedded ID3 USLT") }
    }

    private fun parseSyncedId3Lyrics(payload: ByteArray): EmbeddedLyricsText? {
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
            ?.let { EmbeddedLyricsText(it, "Embedded ID3 SYLT") }
    }

    private fun readFlac(input: InputStream): EmbeddedLyricsText? {
        repeat(MAX_FLAC_METADATA_BLOCKS) {
            val header = input.readExactly(4) ?: return null
            val isLast = (header[0].toInt() and 0x80) != 0
            val type = header[0].toInt() and 0x7F
            val length = ((header[1].toInt() and 0xFF) shl 16) or
                ((header[2].toInt() and 0xFF) shl 8) or
                (header[3].toInt() and 0xFF)
            if (length !in 0..MAX_TAG_BYTES) return null
            val payload = input.readExactly(length) ?: return null
            if (type == FLAC_VORBIS_COMMENT_BLOCK) {
                parseVorbisComments(payload)?.let { return it }
            }
            if (isLast) return null
        }
        return null
    }

    private fun parseVorbisComments(payload: ByteArray): EmbeddedLyricsText? {
        var cursor = 0
        val vendorLength = littleEndianInt32(payload, cursor) ?: return null
        if (vendorLength < 0 || 4 + vendorLength > payload.size) return null
        cursor += 4 + vendorLength
        val count = littleEndianInt32(payload, cursor) ?: return null
        if (count < 0) return null
        cursor += 4

        repeat(count.coerceAtMost(MAX_VORBIS_COMMENTS)) {
            val length = littleEndianInt32(payload, cursor) ?: return null
            cursor += 4
            if (length < 0 || cursor + length > payload.size) return null
            val comment = payload.copyOfRange(cursor, cursor + length).toString(StandardCharsets.UTF_8)
            cursor += length
            val key = comment.substringBefore('=', missingDelimiterValue = "").uppercase()
            val value = comment.substringAfter('=', missingDelimiterValue = "")
            if (key in VorbisLyricsKeys && value.isNotBlank()) {
                return EmbeddedLyricsText(value, "Embedded FLAC $key")
            }
        }
        return null
    }

    private fun id3Encoding(value: Byte): Id3TextEncoding =
        when (value.toInt() and 0xFF) {
            1 -> Id3TextEncoding(Charsets.UTF_16, 2)
            2 -> Id3TextEncoding(Charsets.UTF_16BE, 2)
            3 -> Id3TextEncoding(StandardCharsets.UTF_8, 1)
            else -> Id3TextEncoding(StandardCharsets.ISO_8859_1, 1)
        }

    private fun decodeId3Text(payload: ByteArray, start: Int, end: Int, encoding: Id3TextEncoding): String =
        if (start >= end || start >= payload.size) {
            ""
        } else {
            payload.copyOfRange(start, end.coerceAtMost(payload.size)).toString(encoding.charset).trimEnd('\u0000')
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

    private fun InputStream.readExactly(size: Int): ByteArray? {
        if (size < 0) return null
        val output = ByteArrayOutputStream(size.coerceAtMost(DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var remaining = size
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(buffer.size, remaining))
            if (read < 0) return null
            output.write(buffer, 0, read)
            remaining -= read
        }
        return output.toByteArray()
    }

    private fun syncSafeInt(bytes: ByteArray, start: Int): Int =
        ((bytes[start].toInt() and 0x7F) shl 21) or
            ((bytes[start + 1].toInt() and 0x7F) shl 14) or
            ((bytes[start + 2].toInt() and 0x7F) shl 7) or
            (bytes[start + 3].toInt() and 0x7F)

    private fun int32(bytes: ByteArray, start: Int): Int =
        ((bytes[start].toInt() and 0xFF) shl 24) or
            ((bytes[start + 1].toInt() and 0xFF) shl 16) or
            ((bytes[start + 2].toInt() and 0xFF) shl 8) or
            (bytes[start + 3].toInt() and 0xFF)

    private fun littleEndianInt32(bytes: ByteArray, start: Int): Int? {
        if (start + 4 > bytes.size) return null
        return (bytes[start].toInt() and 0xFF) or
            ((bytes[start + 1].toInt() and 0xFF) shl 8) or
            ((bytes[start + 2].toInt() and 0xFF) shl 16) or
            ((bytes[start + 3].toInt() and 0xFF) shl 24)
    }

    private fun formatLrcTime(timeMs: Int): String {
        val safeMs = timeMs.coerceAtLeast(0)
        val minutes = safeMs / 60_000
        val seconds = (safeMs % 60_000) / 1_000
        val hundredths = (safeMs % 1_000) / 10
        return "%02d:%02d.%02d".format(minutes, seconds, hundredths)
    }

    private data class Id3TextEncoding(
        val charset: Charset,
        val terminatorSize: Int,
    )

    private const val MAX_TAG_BYTES = 2 * 1024 * 1024
    private const val MAX_FLAC_METADATA_BLOCKS = 64
    private const val MAX_VORBIS_COMMENTS = 256
    private const val FLAC_VORBIS_COMMENT_BLOCK = 4
    @Suppress("SpellCheckingInspection")
    private val VorbisLyricsKeys = setOf("LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS")
}

internal data class EmbeddedLyricsText(
    val text: String,
    val sourceLabel: String,
)
