package app.echo.android.data

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

internal object WavTagRewriter {
    fun isWavFile(file: File, mimeType: String?): Boolean {
        if (LibraryWavTagPolicy.isWavContainer(mimeType, file.name)) return true
        return file.inputStream().use { input ->
            val peek = ByteArray(12)
            val read = readFully(input, peek)
            looksLikeRiff(peek, read) || startsWithId3ThenRiff(file)
        }
    }

    fun rewriteFile(
        source: File,
        destination: File,
        fields: AudioTagFields,
    ): AudioTagRewriteStatus {
        val parsed = parse(source) ?: return AudioTagRewriteStatus.InvalidSource
        if (parsed.rf64) return AudioTagRewriteStatus.UnsupportedFormat
        FileOutputStream(destination).use { output ->
            writeParsed(source, output, parsed, fields)
        }
        return AudioTagRewriteStatus.Written
    }

    fun rewrite(
        input: InputStream,
        output: OutputStream,
        fields: AudioTagFields,
    ): AudioTagRewriteStatus {
        val source = File.createTempFile("echo-wav-in-", ".wav")
        val destination = File.createTempFile("echo-wav-out-", ".wav")
        return try {
            FileOutputStream(source).use { input.copyTo(it, COPY_BUFFER) }
            val status = rewriteFile(source, destination, fields)
            if (status == AudioTagRewriteStatus.Written) {
                FileInputStream(destination).use { it.copyTo(output, COPY_BUFFER) }
            }
            status
        } finally {
            source.delete()
            destination.delete()
        }
    }

    private fun startsWithId3ThenRiff(file: File): Boolean =
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(10)
            if (raf.read(header) < 10 || !isId3(header)) return@use false
            val tagSize = syncSafeInt(header, 6)
            if (tagSize !in 1..MAX_TAG_BYTES) return@use false
            raf.seek(10L + tagSize)
            val riff = ByteArray(4)
            raf.read(riff) == 4 && looksLikeRiff(riff, 4)
        }

    private fun parse(file: File): ParsedWav? =
        RandomAccessFile(file, "r").use { raf ->
            var position = 0L
            val id3Header = ByteArray(10)
            if (raf.read(id3Header) == 10 && isId3(id3Header)) {
                val tagSize = syncSafeInt(id3Header, 6)
                if (tagSize !in 1..MAX_TAG_BYTES) return@use null
                position = 10L + tagSize
                raf.seek(position)
            } else {
                raf.seek(0)
            }
            val header = ByteArray(12)
            if (raf.read(header) != 12) return@use null
            if (!looksLikeRiff(header, 12) || header.decodeToString(8, 12) != "WAVE") return@use null
            position += 12
            val declaredSize = u32le(header, 4)
            val rf64 = declaredSize == 0xFFFFFFFFL
            val chunks = ArrayList<WavChunk>()
            var remaining = if (rf64) Long.MAX_VALUE else declaredSize - 4L
            var count = 0
            while (remaining >= 8L && count < MAX_CHUNKS && position + 8 <= raf.length()) {
                raf.seek(position)
                val chunkHeader = ByteArray(8)
                if (raf.read(chunkHeader) != 8) break
                val id = chunkHeader.decodeToString(0, 4)
                val size = u32le(chunkHeader, 4)
                if (size == 0xFFFFFFFFL) return@use ParsedWav(chunks, rf64 = true)
                val dataOffset = position + 8
                val listType = if (id == "LIST" && size >= 4L) {
                    raf.seek(dataOffset)
                    val marker = ByteArray(4)
                    if (raf.read(marker) == 4) marker.decodeToString(0, 4) else null
                } else {
                    null
                }
                val padded = size + (size and 1L)
                chunks += WavChunk(id, dataOffset, size, listType)
                position = dataOffset + padded
                remaining -= 8 + padded
                count += 1
            }
            ParsedWav(chunks, rf64 = rf64)
        }

    private fun writeParsed(
        source: File,
        output: OutputStream,
        parsed: ParsedWav,
        fields: AudioTagFields,
    ) {
        val existingInfo = parsed.chunks.firstOrNull { it.isInfoList }?.let { chunk ->
            readExactly(source, chunk.dataOffset, chunk.size.toInt())
        }
        val existingId3 = parsed.chunks.firstOrNull { it.isId3Chunk }?.let { chunk ->
            readExactly(source, chunk.dataOffset, chunk.size.toInt())
        }
        val infoPayload = encodeInfoList(existingInfo, fields)
        val id3Payload = AudioFileTagRewriter.encodeId3Tag(existingId3, fields)
        val body = ArrayList<WavBodyPart>(parsed.chunks.size + 2)
        var inserted = false
        parsed.chunks.forEach { chunk ->
            if (chunk.isInfoList || chunk.isId3Chunk) return@forEach
            body += WavBodyPart.Copy(chunk)
            if (!inserted && chunk.id == "fmt ") {
                body += WavBodyPart.Memory("LIST", infoPayload)
                body += WavBodyPart.Memory("id3 ", id3Payload)
                inserted = true
            }
        }
        if (!inserted) {
            body.add(0, WavBodyPart.Memory("id3 ", id3Payload))
            body.add(0, WavBodyPart.Memory("LIST", infoPayload))
        }
        val bodySize = body.sumOf { part ->
            val size = when (part) {
                is WavBodyPart.Copy -> part.chunk.size
                is WavBodyPart.Memory -> part.payload.size.toLong()
            }
            8L + size + (size and 1L)
        }
        output.write("RIFF".toByteArray(StandardCharsets.US_ASCII))
        output.write(u32leBytes((4 + bodySize).toInt()))
        output.write("WAVE".toByteArray(StandardCharsets.US_ASCII))
        RandomAccessFile(source, "r").use { raf ->
            body.forEach { part ->
                when (part) {
                    is WavBodyPart.Memory -> writeChunk(output, part.id, part.payload)
                    is WavBodyPart.Copy -> writeFileChunk(raf, output, part.chunk)
                }
            }
        }
    }

    private fun writeChunk(output: OutputStream, id: String, payload: ByteArray) {
        output.write(id.padEnd(4, ' ').take(4).toByteArray(StandardCharsets.US_ASCII))
        output.write(u32leBytes(payload.size))
        output.write(payload)
        if (payload.size and 1 == 1) output.write(0)
    }

    private fun writeFileChunk(raf: RandomAccessFile, output: OutputStream, chunk: WavChunk) {
        output.write(chunk.id.padEnd(4, ' ').take(4).toByteArray(StandardCharsets.US_ASCII))
        output.write(u32leBytes(chunk.size.toInt()))
        raf.seek(chunk.dataOffset)
        var left = chunk.size
        val buffer = ByteArray(COPY_BUFFER)
        while (left > 0L) {
            val n = raf.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (n <= 0) break
            output.write(buffer, 0, n)
            left -= n
        }
        if (chunk.size and 1L == 1L) output.write(0)
    }

    private fun encodeInfoList(existing: ByteArray?, fields: AudioTagFields): ByteArray {
        val values = linkedMapOf<String, ByteArray>()
        existing?.let { parseInfoItems(it) }?.forEach { (key, value) ->
            if (key !in ManagedInfoKeys) values[key] = value
        }
        values["INAM"] = utf8Z(fields.title)
        values["IART"] = utf8Z(fields.artist)
        fields.album?.let { values["IPRD"] = utf8Z(it) }
        fields.trackNumber?.let { values["ITRK"] = utf8Z(it.toString()) }
        fields.year?.let { values["ICRD"] = utf8Z(it.toString()) }
        val payload = ArrayList<Byte>()
        "INFO".toByteArray(StandardCharsets.US_ASCII).forEach { payload += it }
        values.forEach { (id, value) ->
            chunkBytes(id, value).forEach { payload += it }
        }
        return payload.toByteArray()
    }

    private fun parseInfoItems(payload: ByteArray): List<Pair<String, ByteArray>> {
        if (payload.size < 4 || payload.decodeToString(0, 4) != "INFO") return emptyList()
        val items = ArrayList<Pair<String, ByteArray>>()
        var offset = 4
        while (offset + 8 <= payload.size) {
            val id = payload.decodeToString(offset, offset + 4)
            val size = u32le(payload, offset + 4).toInt()
            offset += 8
            if (size < 0 || offset + size > payload.size) break
            items += id to payload.copyOfRange(offset, offset + size)
            offset += size + (size and 1)
        }
        return items
    }

    private fun chunkBytes(id: String, payload: ByteArray): ByteArray {
        val padded = payload.size + (payload.size and 1)
        val buffer = ByteBuffer.allocate(8 + padded).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(id.padEnd(4, ' ').take(4).toByteArray(StandardCharsets.US_ASCII))
        buffer.putInt(payload.size)
        buffer.put(payload)
        if (payload.size and 1 == 1) buffer.put(0)
        return buffer.array()
    }

    private fun utf8Z(value: String): ByteArray = value.toByteArray(StandardCharsets.UTF_8) + 0

    private val WavChunk.isId3Chunk: Boolean
        get() = id == "id3 " || id == "ID3 " || id == "ID3\u0000"

    private val WavChunk.isInfoList: Boolean
        get() = id == "LIST" && listType == "INFO"

    private fun readExactly(file: File, offset: Long, size: Int): ByteArray? {
        if (size < 0 || size > MAX_TAG_BYTES) return null
        return RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            val bytes = ByteArray(size)
            if (raf.read(bytes) != size) null else bytes
        }
    }

    private fun looksLikeRiff(bytes: ByteArray, n: Int): Boolean =
        n >= 4 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()

    private fun isId3(bytes: ByteArray): Boolean =
        bytes.size >= 3 &&
            bytes[0] == 'I'.code.toByte() &&
            bytes[1] == 'D'.code.toByte() &&
            bytes[2] == '3'.code.toByte()

    private fun syncSafeInt(bytes: ByteArray, start: Int): Int =
        ((bytes[start].toInt() and 0x7F) shl 21) or
            ((bytes[start + 1].toInt() and 0x7F) shl 14) or
            ((bytes[start + 2].toInt() and 0x7F) shl 7) or
            (bytes[start + 3].toInt() and 0x7F)

    private fun u32le(bytes: ByteArray, start: Int): Long =
        (bytes[start].toInt() and 0xFF).toLong() or
            ((bytes[start + 1].toInt() and 0xFF).toLong() shl 8) or
            ((bytes[start + 2].toInt() and 0xFF).toLong() shl 16) or
            ((bytes[start + 3].toInt() and 0xFF).toLong() shl 24)

    private fun u32leBytes(value: Int): ByteArray =
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        )

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) break
            offset += read
        }
        return offset
    }

    private data class WavChunk(
        val id: String,
        val dataOffset: Long,
        val size: Long,
        val listType: String? = null,
    )

    private data class ParsedWav(
        val chunks: List<WavChunk>,
        val rf64: Boolean,
    )

    private sealed class WavBodyPart {
        data class Copy(val chunk: WavChunk) : WavBodyPart()
        data class Memory(val id: String, val payload: ByteArray) : WavBodyPart()
    }

    private val ManagedInfoKeys = setOf("INAM", "IART", "IPRD", "ITRK", "IPRT", "ICRD")
    private const val COPY_BUFFER = 64 * 1024
    private const val MAX_CHUNKS = 256
    private const val MAX_TAG_BYTES = 2 * 1024 * 1024
}
