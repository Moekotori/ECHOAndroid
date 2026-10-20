package app.echo.android.data

import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Random access is confined to the cached copy; the caller verifies before replacing the source. */
internal object ContainerTagRewrite {
    fun rewriteFile(source: File, destination: File, fields: AudioTagFields): AudioTagRewriteStatus? {
        val header = source.inputStream().use { it.tagBytes(8) } ?: return null
        return when {
            header.copyOfRange(4, 8).toString(Charsets.ISO_8859_1) == "ftyp" -> Mp4TagRewriter.rewrite(source, destination, fields)
            header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "OggS" ->
                source.inputStream().buffered().use { input -> destination.outputStream().buffered().use { output ->
                    OggTagRewriter.rewrite(input, output, fields)
                } }
            else -> null
        }
    }

    fun rewrite(input: InputStream, output: OutputStream, fields: AudioTagFields): AudioTagRewriteStatus {
        val source = File.createTempFile("echo-container-in-", ".tmp")
        val destination = File.createTempFile("echo-container-out-", ".tmp")
        return try {
            source.outputStream().use { input.copyTo(it) }
            val status = rewriteFile(source, destination, fields) ?: AudioTagRewriteStatus.UnsupportedFormat
            if (status == AudioTagRewriteStatus.Written) destination.inputStream().use { it.copyTo(output) }
            status
        } finally {
            source.delete()
            destination.delete()
        }
    }
}
