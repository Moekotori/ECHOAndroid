package app.echo.android.data

import java.io.InputStream

/** A view of one container element; closing it never closes its owner. */
internal class TagSliceInput(private val source: InputStream, var remaining: Long) : InputStream() {
    override fun read(): Int {
        if (remaining <= 0) return -1
        return source.read().also { if (it >= 0) remaining-- }
    }
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining <= 0) return -1
        return source.read(bytes, offset, minOf(length.toLong(), remaining).toInt())
            .also { if (it > 0) remaining -= it }
    }
    override fun skip(count: Long): Long = source.skip(count.coerceIn(0, remaining))
        .also { remaining -= it }
}

internal fun InputStream.tagBytes(size: Int): ByteArray? {
    if (size < 0) return null
    val bytes = ByteArray(size)
    var offset = 0
    while (offset < size) {
        val count = read(bytes, offset, size - offset)
        if (count < 0) return null
        if (count == 0) {
            val next = read()
            if (next < 0) return null
            bytes[offset++] = next.toByte()
        } else offset += count
    }
    return bytes
}

internal fun InputStream.skipTagBytes(size: Long): Boolean {
    var remaining = size
    val buffer = ByteArray(8192)
    while (remaining > 0) {
        val skipped = skip(remaining)
        if (skipped > 0) remaining -= skipped else {
            val count = read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
            if (count < 0) return false
            if (count == 0) {
                if (read() < 0) return false
                remaining--
            } else remaining -= count
        }
    }
    return true
}

internal fun ByteArray.tagU32(offset: Int): Long =
    (0..3).fold(0L) { value, index -> (value shl 8) or (this[offset + index].toLong() and 255) }

internal fun ByteArray.tagSyncSafe(offset: Int): Int? {
    if (offset + 4 > size || (0..3).any { this[offset + it].toInt() and 128 != 0 }) return null
    return (0..3).fold(0) { value, index -> (value shl 7) or (this[offset + index].toInt() and 127) }
}

internal const val MaxEmbeddedArtworkBytes = 8 * 1024 * 1024
internal const val MaxEmbeddedTextBytes = 2 * 1024 * 1024
