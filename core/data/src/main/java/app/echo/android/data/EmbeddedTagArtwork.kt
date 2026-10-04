package app.echo.android.data

import java.io.InputStream

internal data class TagArtwork(val bytes: ByteArray, val mime: String, val type: Int)

internal object EmbeddedTagArtwork {
    fun readFlac(input: InputStream, length: Int): TagArtwork? {
        val slice = TagSliceInput(input, length.toLong())
        try {
            val header = slice.tagBytes(8) ?: return null
            val mimeSize = header.tagU32(4)
            if (mimeSize !in 1..128 || mimeSize > slice.remaining) return null
            val mime = slice.tagBytes(mimeSize.toInt())?.toString(Charsets.ISO_8859_1) ?: return null
            if (!mime.startsWith("image/")) return null
            val description = slice.tagBytes(4)?.tagU32(0) ?: return null
            if (description > slice.remaining || !slice.skipTagBytes(description)) return null
            val dimensions = slice.tagBytes(20) ?: return null
            val imageSize = dimensions.tagU32(16)
            if (imageSize !in 1..MaxEmbeddedArtworkBytes.toLong() || imageSize > slice.remaining) return null
            return TagArtwork(slice.tagBytes(imageSize.toInt()) ?: return null, mime, header.tagU32(0).toInt())
        } finally {
            slice.skipTagBytes(slice.remaining)
        }
    }
    fun id3PictureType(payload: ByteArray, v22: Boolean = false): Int? =
        pictureHeader(payload, v22)?.second?.let { payload[it].toInt() and 255 }

    fun id3(payload: ByteArray, v22: Boolean = false): TagArtwork? {
        val (mime, typeOffset) = pictureHeader(payload, v22) ?: return null
        val encoding = payload[0].toInt() and 255
        val step = if (encoding == 1 || encoding == 2) 2 else 1
        var cursor = typeOffset + 1
        while (cursor + step <= payload.size) {
            if (payload[cursor] == 0.toByte() && (step == 1 || payload[cursor + 1] == 0.toByte())) {
                cursor += step
                val size = payload.size - cursor
                if (size !in 1..MaxEmbeddedArtworkBytes || mime == "-->") return null
                return TagArtwork(payload.copyOfRange(cursor, payload.size), mime, payload[typeOffset].toInt() and 255)
            }
            cursor += step
        }
        return null
    }

    private fun pictureHeader(payload: ByteArray, v22: Boolean): Pair<String, Int>? {
        if (payload.size < 5) return null
        if (v22) {
            val format = payload.copyOfRange(1, 4).toString(Charsets.ISO_8859_1)
            return (when (format.uppercase()) { "PNG" -> "image/png"; "JPG" -> "image/jpeg"; else -> format }) to 4
        }
        val end = (1 until payload.size).firstOrNull { payload[it] == 0.toByte() } ?: return null
        if (end + 1 >= payload.size) return null
        return payload.copyOfRange(1, end).toString(Charsets.ISO_8859_1) to (end + 1)
    }

    fun flac(payload: ByteArray): TagArtwork? {
        if (payload.size < 32) return null
        val type = payload.tagU32(0).toInt()
        val mimeSize = payload.tagU32(4)
        if (mimeSize > payload.size - 8) return null
        val mimeEnd = 8 + mimeSize.toInt()
        val mime = payload.copyOfRange(8, mimeEnd).toString(Charsets.ISO_8859_1)
        if (mime == "-->" || mimeEnd + 4 > payload.size) return null
        val descriptionSize = payload.tagU32(mimeEnd)
        if (descriptionSize > payload.size - mimeEnd - 4) return null
        val dataOffset = mimeEnd + 4 + descriptionSize.toInt() + 20
        if (dataOffset > payload.size) return null
        val imageSize = payload.tagU32(dataOffset - 4)
        if (imageSize !in 1..MaxEmbeddedArtworkBytes.toLong() || imageSize > payload.size - dataOffset) return null
        return TagArtwork(payload.copyOfRange(dataOffset, dataOffset + imageSize.toInt()), mime, type)
    }
}
