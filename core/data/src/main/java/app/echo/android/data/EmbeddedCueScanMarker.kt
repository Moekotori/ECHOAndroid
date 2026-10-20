package app.echo.android.data

import java.io.File
import java.util.UUID

/** A bounded migration cache. V2 also refreshes legacy whole-file CUE rows lacking provenance. */
internal class EmbeddedCueScanMarker(filesDir: File, scope: String) {
    private val directory = File(filesDir, "embedded-cue-scan-v2")
    private val marker = File(directory, UUID.nameUUIDFromBytes(scope.toByteArray(Charsets.UTF_8)).toString())
    fun isComplete(): Boolean = marker.isFile
    fun complete() {
        runCatching {
            if (!directory.isDirectory && !directory.mkdirs()) return
            val old = directory.listFiles().orEmpty().filter { it.isFile && it != marker }.sortedBy { it.lastModified() }
            old.take((old.size - 63).coerceAtLeast(0)).forEach { it.delete() }
            marker.writeText("1")
        }
    }
}
