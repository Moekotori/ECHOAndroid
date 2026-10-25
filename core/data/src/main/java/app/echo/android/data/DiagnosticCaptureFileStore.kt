package app.echo.android.data

import java.io.File

internal object DiagnosticCapturePolicy {
    const val SegmentBytes = 512 * 1024
    const val DurationMs = 24 * 60 * 60 * 1000L
    fun expired(wall: Long, elapsed: Long, wallDeadline: Long, elapsedDeadline: Long) = wall >= wallDeadline || elapsed >= elapsedDeadline
}

/** Only four fixed names are managed; unrelated files can never enter an export or cleanup. */
internal class DiagnosticCaptureFileStore(private val directory: File, private val segmentBytes: Int = DiagnosticCapturePolicy.SegmentBytes) {
    fun files() = (0..3).map { File(directory, "diagnostic-$it.log") }.filter(File::isFile).asReversed()
    fun clear() { files().forEach { check(it.delete()) } }
    fun append(text: String) {
        check(directory.isDirectory || directory.mkdirs())
        val current = File(directory, "diagnostic-0.log")
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= segmentBytes)
        if (current.length() + bytes.size > segmentBytes) {
            val oldest = File(directory, "diagnostic-3.log")
            check(!oldest.exists() || oldest.delete())
            for (index in 2 downTo 0) {
                val from = File(directory, "diagnostic-$index.log")
                if (from.exists()) check(from.renameTo(File(directory, "diagnostic-${index + 1}.log")))
            }
        }
        current.appendBytes(bytes)
    }
}
