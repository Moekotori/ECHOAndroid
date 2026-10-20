package app.echo.android.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Repaginate header packets, then retain audio lacing/granules and repair sequence numbers/CRCs. */
internal object OggTagRewriter {
    fun rewrite(input: InputStream, output: OutputStream, fields: AudioTagFields): AudioTagRewriteStatus {
        var serial: Int? = null
        var expectedSequence = 0
        var writtenSequence = 0
        var packetNumber = 0
        var identification: ByteArray? = null
        var opus = false
        val packet = ByteArrayOutputStream()
        var headerBytes = 0
        var commentsWritten = false
        var ended = false
        while (true) {
            val first = input.read()
            if (first < 0) return if (commentsWritten && ended) AudioTagRewriteStatus.Written else AudioTagRewriteStatus.InvalidSource
            val header = byteArrayOf(first.toByte()) + (input.tagBytes(26) ?: return AudioTagRewriteStatus.InvalidSource)
            if (header.copyOfRange(0, 4).toString(Charsets.US_ASCII) != "OggS" || header[4] != 0.toByte()) return AudioTagRewriteStatus.InvalidSource
            val laces = input.tagBytes(header[26].toInt() and 255) ?: return AudioTagRewriteStatus.InvalidSource
            val body = input.tagBytes(laces.sumOf { it.toInt() and 255 }) ?: return AudioTagRewriteStatus.InvalidSource
            val page = header + laces + body
            val storedCrc = le(page, 22)
            repeat(4) { page[22 + it] = 0 }
            if (crc(page) != storedCrc) return AudioTagRewriteStatus.InvalidSource
            val pageSerial = le(header, 14)
            val sequence = le(header, 18)
            if (serial == null) {
                serial = pageSerial
                if (sequence != 0 || header[5].toInt() and 3 != 2) return AudioTagRewriteStatus.InvalidSource
            }
            // Multiplexed/chained streams need independent header and sequence ownership.
            if (pageSerial != serial || ended) return AudioTagRewriteStatus.UnsupportedFormat
            if (sequence != expectedSequence++) return AudioTagRewriteStatus.InvalidSource
            ended = header[5].toInt() and 4 != 0
            if (commentsWritten) {
                writePage(output, header, laces, body, writtenSequence++)
                continue
            }
            headerBytes += page.size
            if (headerBytes > VorbisCommentRewriter.MaxBytes || sequence > 1024) return AudioTagRewriteStatus.UnsupportedFormat
            if ((header[5].toInt() and 1 != 0) != (packet.size() > 0)) return AudioTagRewriteStatus.InvalidSource
            var offset = 0
            for (index in laces.indices) {
                val length = laces[index].toInt() and 255
                packet.write(body, offset, length)
                offset += length
                if (packet.size() > VorbisCommentRewriter.MaxBytes) return AudioTagRewriteStatus.UnsupportedFormat
                if (length == 255) continue
                val bytes = packet.toByteArray()
                packet.reset()
                if (packetNumber++ == 0) {
                    opus = starts(bytes, "OpusHead")
                    if (!opus && !(bytes.size >= 7 && bytes[0] == 1.toByte() && starts(bytes.copyOfRange(1, bytes.size), "vorbis"))) return AudioTagRewriteStatus.UnsupportedFormat
                    identification = bytes
                } else {
                    val prefix = if (opus) "OpusTags".toByteArray() else byteArrayOf(3) + "vorbis".toByteArray()
                    if (bytes.size < prefix.size || !bytes.copyOfRange(0, prefix.size).contentEquals(prefix)) return AudioTagRewriteStatus.InvalidSource
                    if (!opus && (bytes.last().toInt() and 1 == 0)) return AudioTagRewriteStatus.InvalidSource
                    val payload = bytes.copyOfRange(prefix.size, bytes.size - if (opus) 0 else 1)
                    val updated = VorbisCommentRewriter.rewrite(payload, fields) ?: return AudioTagRewriteStatus.UnsupportedFormat
                    writtenSequence = writePacket(output, identification!!, serial, writtenSequence, bos = true)
                    val comment = prefix + updated + if (opus) byteArrayOf() else byteArrayOf(1)
                    writtenSequence = writePacket(output, comment, serial, writtenSequence, bos = false)
                    val tailLaces = laces.copyOfRange(index + 1, laces.size)
                    if (tailLaces.isNotEmpty()) {
                        val tailHeader = header.copyOf()
                        tailHeader[5] = (header[5].toInt() and 4).toByte()
                        writePage(output, tailHeader, tailLaces, body.copyOfRange(offset, body.size), writtenSequence++)
                    } else if (ended) return AudioTagRewriteStatus.InvalidSource
                    commentsWritten = true
                    break
                }
            }
        }
    }

    private fun writePacket(output: OutputStream, bytes: ByteArray, serial: Int, startSequence: Int, bos: Boolean): Int {
        var sequence = startSequence
        val allLaces = ByteArray(bytes.size / 255 + 1) { if (it == bytes.size / 255) (bytes.size % 255).toByte() else 255.toByte() }
        var segment = 0
        var offset = 0
        while (segment < allLaces.size) {
            val laces = allLaces.copyOfRange(segment, minOf(segment + 255, allLaces.size))
            val size = laces.sumOf { it.toInt() and 255 }
            val header = ByteArray(27)
            "OggS".toByteArray().copyInto(header)
            header[5] = (if (segment > 0) 1 else if (bos) 2 else 0).toByte()
            if (segment + laces.size < allLaces.size) repeat(8) { header[6 + it] = 255.toByte() }
            putLe(header, 14, serial)
            writePage(output, header, laces, bytes.copyOfRange(offset, offset + size), sequence++)
            segment += laces.size
            offset += size
        }
        return sequence
    }

    private fun writePage(output: OutputStream, original: ByteArray, laces: ByteArray, body: ByteArray, sequence: Int) {
        val header = original.copyOf()
        header[26] = laces.size.toByte()
        putLe(header, 18, sequence)
        putLe(header, 22, 0)
        val page = header + laces + body
        putLe(page, 22, crc(page))
        output.write(page)
    }

    private fun starts(bytes: ByteArray, prefix: String) = bytes.size >= prefix.length && bytes.copyOfRange(0, prefix.length).toString(Charsets.US_ASCII) == prefix
    private fun le(bytes: ByteArray, offset: Int) = (0..3).fold(0) { n, i -> n or ((bytes[offset + i].toInt() and 255) shl (i * 8)) }
    private fun putLe(bytes: ByteArray, offset: Int, n: Int) { repeat(4) { bytes[offset + it] = (n ushr (it * 8)).toByte() } }
    private fun crc(bytes: ByteArray): Int = bytes.fold(0) { value, byte ->
        (value shl 8) xor CrcTable[((value ushr 24) xor (byte.toInt() and 255)) and 255]
    }
    private val CrcTable = IntArray(256) { index ->
        var value = index shl 24
        repeat(8) { value = (value shl 1) xor if (value < 0) 0x04c11db7 else 0 }
        value
    }
}
