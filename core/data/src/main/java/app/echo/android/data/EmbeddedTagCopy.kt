package app.echo.android.data

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** The limit applies to actual bytes, independent of a provider's size metadata. */
internal fun copyEmbeddedTagSource(input: InputStream, output: OutputStream, maxBytes: Long): Long {
    require(maxBytes in 0 until Long.MAX_VALUE)
    val buffer = ByteArray(64 * 1024)
    var copied = 0L
    while (true) {
        // Probe at most one byte beyond the budget, without writing the excess byte.
        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), maxBytes - copied + 1L).toInt())
        if (read < 0) return copied
        if (read == 0) continue
        if (read.toLong() > maxBytes - copied) throw IOException("Audio exceeds tag rewrite copy budget")
        output.write(buffer, 0, read)
        copied += read
    }
}
