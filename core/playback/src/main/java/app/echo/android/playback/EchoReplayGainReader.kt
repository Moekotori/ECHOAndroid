package app.echo.android.playback

import app.echo.android.model.playback.EchoReplayGainTags
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

object EchoReplayGainReader {
    fun readTrackGainDb(input: InputStream): Float? =
        readTags(input).selectedGainDb(app.echo.android.model.playback.EchoReplayGainMode.Auto)

    fun readTags(input: InputStream): EchoReplayGainTags {
        val header = ByteArray(10)
        val read = input.readFully(header)
        if (read < 3) return EchoReplayGainTags()
        return when {
            isId3(header, read) -> {
                if (read < 10) return EchoReplayGainTags()
                val id3 = readId3Tags(input, header)
                val next = ByteArray(4)
                val nextRead = input.readFully(next)
                if (isFlac(next, nextRead)) {
                    id3.orElse(readFlacTags(input))
                } else {
                    id3
                }
            }
            isFlac(header, read) -> {
                val rest = PrefixInputStream(header.copyOfRange(4, read), input)
                readFlacTags(rest)
            }
            else -> EchoReplayGainTags()
        }
    }

    private fun readId3Tags(input: InputStream, header: ByteArray): EchoReplayGainTags {
        val majorVersion = header[3].toInt()
        if (majorVersion !in 2..4) return EchoReplayGainTags()
        val tagSize = syncSafeInt(header, 6).takeIf { it in 1..MAX_TAG_BYTES }
            ?: return EchoReplayGainTags()
        val frames = ByteArrayInputStream(input.readExactly(tagSize) ?: return EchoReplayGainTags())
        var trackGain: Float? = null
        var albumGain: Float? = null
        while (frames.available() >= 10) {
            val frameHeader = frames.readExactly(10) ?: break
            val frameId = frameHeader.copyOfRange(0, 4).toString(StandardCharsets.ISO_8859_1)
            if (frameId.all { it.code == 0 }) break
            val frameSize = if (majorVersion == 4) syncSafeInt(frameHeader, 4) else int32(frameHeader)
            if (frameSize <= 0 || frameSize > frames.available()) break
            val payload = frames.readExactly(frameSize) ?: break
            if (frameId == "TXXX") {
                val entry = parseUserTextFrame(payload)
                when (entry?.description?.uppercase()) {
                    "REPLAYGAIN_TRACK_GAIN" -> trackGain = parseGainDb(entry.value)
                    "REPLAYGAIN_ALBUM_GAIN" -> albumGain = parseGainDb(entry.value)
                }
            }
        }
        return EchoReplayGainTags(trackGainDb = trackGain, albumGainDb = albumGain)
    }

    private fun parseUserTextFrame(payload: ByteArray): UserTextEntry? {
        if (payload.size <= 2) return null
        val encoding = id3Encoding(payload[0])
        val descriptionEnd = findTerminator(payload, encoding) ?: return null
        val description = decodeId3Text(payload, 1, descriptionEnd, encoding)
        val valueStart = descriptionEnd + encoding.terminatorSize
        val value = decodeId3Text(payload, valueStart, payload.size, encoding)
        return UserTextEntry(description, value)
    }

    private fun readFlacTags(input: InputStream): EchoReplayGainTags {
        repeat(MAX_FLAC_METADATA_BLOCKS) {
            val header = input.readExactly(4) ?: return EchoReplayGainTags()
            val isLast = (header[0].toInt() and 0x80) != 0
            val type = header[0].toInt() and 0x7F
            val length = ((header[1].toInt() and 0xFF) shl 16) or
                ((header[2].toInt() and 0xFF) shl 8) or
                (header[3].toInt() and 0xFF)
            if (length < 0) return EchoReplayGainTags()
            if (type == FLAC_VORBIS_COMMENT_BLOCK) {
                if (length !in 0..MAX_TAG_BYTES) return EchoReplayGainTags()
                val payload = input.readExactly(length) ?: return EchoReplayGainTags()
                return parseVorbisReplayGainTags(payload)
            }
            if (length > MAX_TAG_BYTES) {
                if (!input.skipFully(length)) return EchoReplayGainTags()
            } else {
                if (input.readExactly(length) == null) return EchoReplayGainTags()
            }
            if (isLast) return EchoReplayGainTags()
        }
        return EchoReplayGainTags()
    }

    private fun parseVorbisReplayGainTags(payload: ByteArray): EchoReplayGainTags {
        var cursor = 0
        val vendorLength = littleEndianInt32(payload, cursor) ?: return EchoReplayGainTags()
        if (vendorLength < 0 || 4 + vendorLength > payload.size) return EchoReplayGainTags()
        cursor += 4 + vendorLength
        val count = littleEndianInt32(payload, cursor) ?: return EchoReplayGainTags()
        if (count < 0) return EchoReplayGainTags()
        cursor += 4
        var trackGain: Float? = null
        var albumGain: Float? = null
        repeat(count.coerceAtMost(MAX_VORBIS_COMMENTS)) {
            val length = littleEndianInt32(payload, cursor) ?: return@repeat
            cursor += 4
            if (length < 0 || cursor + length > payload.size) return@repeat
            val comment = payload.copyOfRange(cursor, cursor + length).toString(StandardCharsets.UTF_8)
            cursor += length
            val key = comment.substringBefore('=', missingDelimiterValue = "").uppercase()
            val value = comment.substringAfter('=', missingDelimiterValue = "")
            when (key) {
                "REPLAYGAIN_TRACK_GAIN" -> trackGain = parseGainDb(value)
                "REPLAYGAIN_ALBUM_GAIN" -> albumGain = parseGainDb(value)
            }
        }
        return EchoReplayGainTags(trackGainDb = trackGain, albumGainDb = albumGain)
    }

    private fun parseGainDb(value: String): Float? {
        val match = GainPattern.find(value) ?: return null
        return match.value.toFloatOrNull()?.takeIf { it in MIN_REPLAY_GAIN_DB..MAX_REPLAY_GAIN_DB }
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

    private fun findTerminator(payload: ByteArray, encoding: Id3TextEncoding): Int? {
        var index = 1
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
        val output = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = read(output, offset, size - offset)
            if (read < 0) return null
            offset += read
        }
        return output
    }

    private fun InputStream.readFully(buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = read(buffer, offset, buffer.size - offset)
            if (read < 0) break
            offset += read
        }
        return offset
    }

    private fun InputStream.skipFully(size: Int): Boolean {
        var remaining = size
        val discard = ByteArray(SKIP_BUFFER_BYTES)
        while (remaining > 0) {
            val skipped = skip(remaining.toLong()).toInt()
            if (skipped > 0) {
                remaining -= skipped
                continue
            }
            val toRead = minOf(remaining, discard.size)
            val read = read(discard, 0, toRead)
            if (read < 0) return false
            remaining -= read
        }
        return true
    }

    private fun syncSafeInt(bytes: ByteArray, start: Int): Int =
        ((bytes[start].toInt() and 0x7F) shl 21) or
            ((bytes[start + 1].toInt() and 0x7F) shl 14) or
            ((bytes[start + 2].toInt() and 0x7F) shl 7) or
            (bytes[start + 3].toInt() and 0x7F)

    private fun int32(bytes: ByteArray): Int =
        ((bytes[4].toInt() and 0xFF) shl 24) or
            ((bytes[5].toInt() and 0xFF) shl 16) or
            ((bytes[6].toInt() and 0xFF) shl 8) or
            (bytes[7].toInt() and 0xFF)

    private fun littleEndianInt32(bytes: ByteArray, start: Int): Int? {
        if (start + 4 > bytes.size) return null
        return (bytes[start].toInt() and 0xFF) or
            ((bytes[start + 1].toInt() and 0xFF) shl 8) or
            ((bytes[start + 2].toInt() and 0xFF) shl 16) or
            ((bytes[start + 3].toInt() and 0xFF) shl 24)
    }

    private fun isId3(bytes: ByteArray, size: Int): Boolean =
        size >= 3 &&
            bytes[0] == 'I'.code.toByte() &&
            bytes[1] == 'D'.code.toByte() &&
            bytes[2] == '3'.code.toByte()

    private fun isFlac(bytes: ByteArray, size: Int): Boolean =
        size >= 4 &&
            bytes[0] == 'f'.code.toByte() &&
            bytes[1] == 'L'.code.toByte() &&
            bytes[2] == 'a'.code.toByte() &&
            bytes[3] == 'C'.code.toByte()

    private data class UserTextEntry(
        val description: String,
        val value: String,
    )

    private data class Id3TextEncoding(
        val charset: Charset,
        val terminatorSize: Int,
    )

    private const val MAX_TAG_BYTES = 2 * 1024 * 1024
    private const val MAX_FLAC_METADATA_BLOCKS = 64
    private const val MAX_VORBIS_COMMENTS = 256
    private const val FLAC_VORBIS_COMMENT_BLOCK = 4
    private const val SKIP_BUFFER_BYTES = 8 * 1024
    private const val MIN_REPLAY_GAIN_DB = -40f
    private const val MAX_REPLAY_GAIN_DB = 20f
    private val GainPattern = Regex("[+-]?\\d+(?:\\.\\d+)?")
}

private class PrefixInputStream(
    private val prefix: ByteArray,
    private val rest: InputStream,
) : InputStream() {
    private var offset = 0

    override fun read(): Int {
        if (offset < prefix.size) {
            val value = prefix[offset].toInt() and 0xFF
            offset += 1
            return value
        }
        return rest.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len <= 0) return 0
        var written = 0
        while (offset < prefix.size && written < len) {
            b[off + written] = prefix[offset]
            offset += 1
            written += 1
        }
        if (written == len) return written
        val fromRest = rest.read(b, off + written, len - written)
        if (fromRest < 0) return if (written == 0) -1 else written
        return written + fromRest
    }

    override fun close() {
        rest.close()
    }
}
