package app.echo.android.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.extractor.ExtractorInput
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.ByteBuffer

/** Loader-thread AVIO bridge. Sequential reads consume Media3's input and respect its backpressure. */
@UnstableApi
internal class EchoFfmpegInput(private val uri: Uri?, private val sources: DataSource.Factory?) {
    private var input: ExtractorInput? = null
    private var alternate: DataSource? = null
    private var alternatePosition = -1L
    private val bytes = ByteArray(32 * 1024)
    var position = 0L
        private set
    var opening = true
    private var probeBytes = 0L
    private var probeSeeks = 0
    private var probeStarted = System.nanoTime()

    fun bind(input: ExtractorInput) { this.input = input }
    fun beginProbe() {
        closeAlternate()
        position = input!!.position
        opening = true
        probeBytes = 0
        probeSeeks = 0
        probeStarted = System.nanoTime()
    }
    fun canSeek(): Boolean = uri != null && sources != null && input!!.length != C.LENGTH_UNSET.toLong()

    // Called only by FFmpeg's custom AVIO, on the Media3 loader thread.
    fun read(target: ByteBuffer, requested: Int): Int {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
        if (opening && (probeBytes > 8 * 1024 * 1024 || System.nanoTime() - probeStarted > 20_000_000_000L)) {
            throw IOException("Audio container probe exceeded its budget")
        }
        val source = requireNotNull(input)
        val count = minOf(requested, bytes.size)
        val read = if (position == source.position) {
            closeAlternate()
            source.read(bytes, 0, count)
        } else {
            if (!canSeek()) throw IOException("Audio source cannot seek")
            if (alternatePosition != position) {
                closeAlternate()
                val opened = sources!!.createDataSource()
                alternate = opened
                opened.open(DataSpec.Builder().setUri(uri!!).setPosition(position).build())
                alternatePosition = position
            }
            alternate!!.read(bytes, 0, count).also { if (it > 0) alternatePosition += it }
        }
        if (read > 0) {
            target.put(bytes, 0, read)
            position += read
            if (opening) probeBytes += read
        }
        return read
    }

    fun seek(offset: Long, whence: Int): Long {
        val length = input!!.length
        if (whence and 0x10000 != 0) return length // AVSEEK_SIZE; no I/O.
        if (!canSeek()) return -1
        if (opening && ++probeSeeks > 128) throw IOException("Audio container seek budget exceeded")
        val base = when (whence and 0xffff) { 0 -> 0L; 1 -> position; 2 -> length; else -> return -1 }
        val target = base + offset
        if (target < 0 || (offset > 0 && target < base) || target > length) return -1
        position = target
        return target
    }

    /** Close out-of-order probe reads before handing positioning back to Media3. */
    fun closeAlternate() {
        val old = alternate
        alternate = null
        alternatePosition = -1
        old?.close()
    }

    fun close() {
        input = null
        closeAlternate()
    }
}
