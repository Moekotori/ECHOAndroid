package app.echo.android.data

import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/** Read bounded metadata while retaining seekable tail tags. The caller owns [input]. */
fun readBoundedEmbeddedLyrics(input: InputStream, maxBytes: Int = 2 * 1024 * 1024, checkActive: () -> Unit = {}): String? {
    require(maxBytes > 0)
    var failure: IOException? = null
    val bounded = object : FilterInputStream(input), TagTailReader {
        private var remaining = maxBytes
        private fun <T> checked(read: () -> T): T {
            checkActive()
            return try { read() } catch (error: IOException) { failure = error; throw error }
        }
        private fun atLimit(): Int {
            if (`in`.read() < 0) return -1
            throw IOException("Embedded lyrics read limit")
        }
        override fun read(): Int = checked {
            if (remaining == 0) atLimit() else `in`.read().also { if (it >= 0) remaining-- }
        }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int = checked {
            if (length == 0) 0 else if (remaining == 0) atLimit() else
                `in`.read(bytes, offset, minOf(length, remaining)).also { if (it > 0) remaining -= it }
        }
        override fun skip(count: Long): Long = checked {
            if (count <= 0L) 0L else {
                if (count > remaining) throw IOException("Embedded lyrics skip limit")
                `in`.skip(count).also { remaining -= it.toInt() }
            }
        }
        override fun tail(count: Int): ByteArray? = checked {
            if (count > remaining) throw IOException("Embedded lyrics tail limit")
            if (input !is FileInputStream && input !is TagTailReader) throw IOException("Audio tail is not seekable")
            remaining -= count
            readTagTail(input, count)
        }
    }
    val lyrics = readLocalAudioTags(bounded)?.lyrics?.takeIf(String::isNotBlank)
    // A failed optional tag must not mask readable lyrics, or turn an unknown result into "missing".
    if (lyrics == null) failure?.let { throw it }
    return lyrics
}
