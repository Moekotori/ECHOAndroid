package app.echo.android

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

internal object ReplayGainReader {
    fun readTrackGainDb(input: InputStream): Float? {
        val header = ByteArray(10)
        if (input.read(header) < header.size) return null
        return when {
            header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte() ->
                readId3TrackGain(input, header)

            header[0] == 'f'.code.toByte() &&
                header[1] == 'L'.code.toByte() &&
                header[2] == 'a'.code.toByte() &&
                header[3] == 'C'.code.toByte() ->
                readFlacTrackGain(input)

            else -> null
        }
    }

    private fun readId3TrackGain(input: InputStream, header: ByteArray): Float? {
        val majorVersion = header[3].toInt()
        if (majorVersion !in 2..4) return null
        val tagSize = syncSafeInt(header, 6).takeIf { it in 1..MaxTagBytes } ?: return null
        val frames = ByteArrayInputStream(input.readExactly(tagSize) ?: return null)
        var albumGain: Float? = null
        while (frames.available() >= 10) {
            val frameHeader = frames.readExactly(10) ?: return null
            val frameId = frameHeader.copyOfRange(0, 4).toString(StandardCharsets.ISO_8859_1)
            if (frameId.all { it.code == 0 }) return albumGain
            val frameSize = if (majorVersion == 4) syncSafeInt(frameHeader, 4) else int32(frameHeader, 4)
            if (frameSize <= 0 || frameSize > frames.available()) return albumGain
            val payload = frames.readExactly(frameSize) ?: return albumGain
            if (frameId == "TXXX") {
                val entry = parseUserTextFrame(payload)
                when (entry?.description?.uppercase()) {
                    "REPLAYGAIN_TRACK_GAIN" -> return parseGainDb(entry.value)
                    "REPLAYGAIN_ALBUM_GAIN" -> albumGain = parseGainDb(entry.value)
                }
            }
        }
        return albumGain
    }

    private fun parseUserTextFrame(payload: ByteArray): UserTextEntry? {
        if (payload.size <= 2) return null
        val encoding = id3Encoding(payload[0])
        val descriptionEnd = findTerminator(payload, start = 1, encoding = encoding) ?: return null
        val description = decodeId3Text(payload, 1, descriptionEnd, encoding)
        val valueStart = descriptionEnd + encoding.terminatorSize
        val value = decodeId3Text(payload, valueStart, payload.size, encoding)
        return UserTextEntry(description, value)
    }

    private fun readFlacTrackGain(input: InputStream): Float? {
        repeat(MaxFlacMetadataBlocks) {
            val header = input.readExactly(4) ?: return null
            val isLast = (header[0].toInt() and 0x80) != 0
            val type = header[0].toInt() and 0x7F
            val length = ((header[1].toInt() and 0xFF) shl 16) or
                ((header[2].toInt() and 0xFF) shl 8) or
                (header[3].toInt() and 0xFF)
            if (length !in 0..MaxTagBytes) return null
            val payload = input.readExactly(length) ?: return null
            if (type == FlacVorbisCommentBlock) {
                parseVorbisReplayGain(payload)?.let { return it }
            }
            if (isLast) return null
        }
        return null
    }

    private fun parseVorbisReplayGain(payload: ByteArray): Float? {
        var cursor = 0
        val vendorLength = littleEndianInt32(payload, cursor) ?: return null
        if (vendorLength < 0 || 4 + vendorLength > payload.size) return null
        cursor += 4 + vendorLength
        val count = littleEndianInt32(payload, cursor) ?: return null
        if (count < 0) return null
        cursor += 4

        var albumGain: Float? = null
        repeat(count.coerceAtMost(MaxVorbisComments)) {
            val length = littleEndianInt32(payload, cursor) ?: return null
            cursor += 4
            if (length < 0 || cursor + length > payload.size) return null
            val comment = payload.copyOfRange(cursor, cursor + length).toString(StandardCharsets.UTF_8)
            cursor += length
            val key = comment.substringBefore('=', missingDelimiterValue = "").uppercase()
            val value = comment.substringAfter('=', missingDelimiterValue = "")
            when (key) {
                "REPLAYGAIN_TRACK_GAIN" -> return parseGainDb(value)
                "REPLAYGAIN_ALBUM_GAIN" -> albumGain = parseGainDb(value)
            }
        }
        return albumGain
    }

    private fun parseGainDb(value: String): Float? {
        val match = GainPattern.find(value) ?: return null
        return match.value.toFloatOrNull()?.takeIf { it in MinReplayGainDb..MaxReplayGainDb }
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

    private data class UserTextEntry(
        val description: String,
        val value: String,
    )

    private data class Id3TextEncoding(
        val charset: java.nio.charset.Charset,
        val terminatorSize: Int,
    )

    private const val MaxTagBytes = 2 * 1024 * 1024
    private const val MaxFlacMetadataBlocks = 64
    private const val MaxVorbisComments = 256
    private const val FlacVorbisCommentBlock = 4
    private const val MinReplayGainDb = -40f
    private const val MaxReplayGainDb = 20f
    private val GainPattern = Regex("[+-]?\\d+(?:\\.\\d+)?")
}
