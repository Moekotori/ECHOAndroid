package app.echo.android.data

import java.io.IOException
import java.io.InputStream

/** Small range cache for remote files. Skipping audio performs no network read. */
internal class RandomAccessTagInput(
    private val length: Long,
    private val checkActive: () -> Unit = {},
    private val readRange: (Long, Int) -> ByteArray,
) : InputStream() {
    private var position = 0L
    private var cacheStart = -1L
    private var cache = ByteArray(0)
    private var transferred = 0
    private var requests = 0
    private val startedAt = System.nanoTime()

    override fun read(): Int {
        if (!fill()) return -1
        return (cache[(position++ - cacheStart).toInt()].toInt() and 255)
    }
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (!fill()) return -1
        val start = (position - cacheStart).toInt()
        val count = minOf(length, cache.size - start)
        cache.copyInto(bytes, offset, start, start + count)
        position += count
        return count
    }
    override fun skip(count: Long): Long {
        checkActive()
        val skipped = count.coerceIn(0, (length - position).coerceAtLeast(0))
        position += skipped
        return skipped
    }
    fun tail(count: Int): ByteArray? {
        if (length < count) return null
        val previous = position
        position = length - count
        return try { tagBytes(count) } finally { position = previous }
    }
    private fun fill(): Boolean {
        checkActive()
        if (position >= length) return false
        if (position >= cacheStart && position - cacheStart < cache.size) return true
        if (requests >= 64 || transferred >= MaxTransfer || System.nanoTime() - startedAt > MaxReadNanos) {
            throw IOException("Embedded tag range budget exceeded")
        }
        val count = minOf(ChunkBytes.toLong(), length - position, (MaxTransfer - transferred).toLong()).toInt()
        requests++
        cacheStart = position
        cache = readRange(position, count)
        if (cache.isEmpty() || cache.size > count) throw IOException("Invalid embedded tag range")
        transferred += cache.size
        return true
    }
    private companion object {
        const val ChunkBytes = 256 * 1024
        const val MaxTransfer = 12 * 1024 * 1024
        const val MaxReadNanos = 20_000_000_000L
    }
}
