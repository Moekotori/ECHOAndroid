package app.echo.android.data

import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException

/** A successful platform fallback cannot turn failed original-tag I/O into a cacheable success. */
internal class ScanMetadataReads {
    var failed: Boolean = false
        private set

    fun <T> optional(open: () -> InputStream?, reader: (InputStream) -> T): T? = try {
        val input = open() ?: throw IOException("Cannot open audio metadata")
        readScanMetadata(input, reader)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        failed = true
        null
    }
}
