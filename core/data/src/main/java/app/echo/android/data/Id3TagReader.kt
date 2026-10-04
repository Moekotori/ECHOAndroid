package app.echo.android.data

import java.io.InputStream

internal data class ReadId3Frame(val id: String, val flags: ByteArray, val payload: ByteArray)
internal data class ReadId3Tag(val version: Int, val frames: List<ReadId3Frame>)

/** Read selected frames individually. Large pictures never hide text after them. */
internal object Id3TagReader {
    fun read(header: ByteArray, input: InputStream): ReadId3Tag? {
        val version = header[3].toInt() and 255
        if (version !in 2..4) return null
        val size = header.tagSyncSafe(6) ?: return null
        if (size !in 1..MaxTagBytes) return null
        val raw = TagSliceInput(input, size.toLong())
        val flags = header[5].toInt() and 255
        // v2.2 compression is tag-wide and cannot be treated as frames.
        if (version == 2 && flags and 0x40 != 0) return null
        val body: InputStream = if (version < 4 && flags and 0x80 != 0) UnsyncInput(raw) else raw
        if (version >= 3 && flags and 0x40 != 0) {
            val ext = body.tagBytes(4) ?: return null
            val extra = if (version == 4) (ext.tagSyncSafe(0) ?: return null).toLong() - 4 else ext.tagU32(0)
            if (extra < 0 || extra > raw.remaining || !body.skipTagBytes(extra)) return null
        }
        val frames = ArrayList<ReadId3Frame>()
        var retained = 0
        var retainedPicture = false
        var frontPicture = false
        repeat(512) {
            if (raw.remaining <= 0) return ReadId3Tag(if (version == 2) 4 else version, frames)
            val frameHeader = body.tagBytes(if (version == 2) 6 else 10)
                ?: return ReadId3Tag(if (version == 2) 4 else version, frames)
            if (frameHeader[0] == 0.toByte()) {
                body.skipTagBytes(raw.remaining)
                return ReadId3Tag(if (version == 2) 4 else version, frames)
            }
            val originalId = frameHeader.copyOfRange(0, if (version == 2) 3 else 4).toString(Charsets.ISO_8859_1)
            if (originalId.any { it !in 'A'..'Z' && it !in '0'..'9' }) return null
            val length = when (version) {
                2 -> ((frameHeader[3].toInt() and 255) shl 16) or
                    ((frameHeader[4].toInt() and 255) shl 8) or (frameHeader[5].toInt() and 255)
                4 -> (frameHeader.tagSyncSafe(4) ?: return null).toLong()
                else -> frameHeader.tagU32(4)
            }.toLong()
            if (length > raw.remaining) return null
            val id = if (version == 2) V22Ids[originalId] ?: originalId else originalId
            val picture = id == "APIC" || id == "PIC"
            val frameFlags = if (version == 2) byteArrayOf(0, 0) else frameHeader.copyOfRange(8, 10)
            if (version == 4 && flags and 0x80 != 0) frameFlags[1] = (frameFlags[1].toInt() or 2).toByte()
            val cap = if (picture) MaxEmbeddedArtworkBytes + 4096 else MaxEmbeddedTextBytes
            if (id !in ReadableIds || length > cap || retained + length > MaxRetainedBytes || (picture && frontPicture)) {
                if (!body.skipTagBytes(length)) return null
            } else {
                val payload = body.tagBytes(length.toInt()) ?: return null
                // Keep at most one picture, preferring the front cover over a back cover.
                val type = if (picture) EmbeddedTagArtwork.id3PictureType(payload, id == "PIC") else null
                if (!picture || !retainedPicture || type == 3) {
                    if (picture && retainedPicture) {
                        val previous = frames.indexOfFirst { it.id == "APIC" || it.id == "PIC" }
                        if (previous >= 0) retained -= frames.removeAt(previous).payload.size
                    }
                    frames += ReadId3Frame(id, frameFlags, payload)
                    retained += payload.size
                    if (picture) { retainedPicture = true; frontPicture = type == 3 }
                }
            }
        }
        body.skipTagBytes(raw.remaining)
        return ReadId3Tag(if (version == 2) 4 else version, frames)
    }

    private class UnsyncInput(private val source: InputStream) : InputStream() {
        private var previous = -1
        override fun read(): Int {
            var next = source.read()
            if (previous == 255 && next == 0) next = source.read()
            previous = next
            return next
        }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            var count = 0
            while (count < length) {
                val next = read()
                if (next < 0) return if (count == 0) -1 else count
                bytes[offset + count++] = next.toByte()
            }
            return count
        }
    }

    private const val MaxTagBytes = 64 * 1024 * 1024
    private const val MaxRetainedBytes = MaxEmbeddedArtworkBytes + MaxEmbeddedTextBytes + 8192
    private val V22Ids = mapOf("TT2" to "TIT2", "TP1" to "TPE1", "TAL" to "TALB", "TP2" to "TPE2",
        "TRK" to "TRCK", "TPA" to "TPOS", "TYE" to "TYER", "TCM" to "TCOM", "TCO" to "TCON", "ULT" to "USLT", "TXX" to "TXXX")
    private val ReadableIds = setOf("TIT2", "TPE1", "TALB", "TPE2", "TRCK", "TPOS", "TYER", "TDRC",
        "TCOM", "TCON", "USLT", "TXXX", "APIC", "PIC")
}
