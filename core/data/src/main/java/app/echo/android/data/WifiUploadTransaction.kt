package app.echo.android.data

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream

internal data class PendingWifiUpload(
    val id: String, val tree: String, val parent: String, val temporaryName: String,
    val finalName: String, val mime: String, val document: String? = null,
)

/** These boundaries let interruption/recovery tests exercise the real copy and cleanup lifecycle. */
internal interface WifiUploadJournal {
    fun entries(): List<PendingWifiUpload>
    fun put(upload: PendingWifiUpload)
    fun remove(id: String)
}

internal interface WifiUploadFiles {
    fun create(upload: PendingWifiUpload): String
    fun open(document: String): OutputStream
    fun publish(upload: PendingWifiUpload)
    /** Only this entry's temporary names may be deleted; completed files are never cleanup targets. */
    fun deleteTemporary(upload: PendingWifiUpload): Boolean
}

internal class WifiUploadTransaction(private val journal: WifiUploadJournal, private val files: WifiUploadFiles) {
    fun receive(upload: PendingWifiUpload, length: Long, input: InputStream) {
        // Persist the unique temporary name before create: recovery also covers death before create returns.
        journal.put(upload)
        var owned = upload
        var published = false
        try {
            checkActive()
            owned = upload.copy(document = files.create(upload))
            journal.put(owned)
            files.open(checkNotNull(owned.document)).use { output ->
                val buffer = ByteArray(64 * 1024)
                var remaining = length
                while (remaining > 0) {
                    checkActive()
                    val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                    checkActive()
                    if (count < 0) throw EOFException("incomplete_upload")
                    if (count == 0) continue
                    output.write(buffer, 0, count)
                    remaining -= count
                }
                output.flush()
            }
            checkActive()
            files.publish(owned)
            published = true
            // A stale journal after a completed rename is safe: recovery checks the temporary name.
            runCatching { journal.remove(owned.id) }
        } catch (failure: Exception) {
            if (!published && !cleanup(owned)) throw IOException("cleanup_pending", failure)
            throw failure
        }
    }

    fun recover(tree: String) {
        val pending = journal.entries().filter { it.tree == tree }
        var clean = true
        pending.forEach { if (!cleanup(it)) clean = false }
        if (!clean) throw IOException("cleanup_pending")
    }

    private fun cleanup(upload: PendingWifiUpload): Boolean {
        // shutdownNow interrupts the writer. Cleanup still needs to perform provider/journal I/O.
        val interrupted = Thread.interrupted()
        return try {
            if (!files.deleteTemporary(upload)) false
            else { journal.remove(upload.id); true }
        } catch (_: Exception) { false }
        finally { if (interrupted) Thread.currentThread().interrupt() }
    }

    private fun checkActive() {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("cancelled")
    }
}
