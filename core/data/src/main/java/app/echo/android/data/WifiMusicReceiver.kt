package app.echo.android.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.echo.android.model.connect.EchoWifiTransferPolicy
import java.io.EOFException
import java.io.InputStream
import java.io.InterruptedIOException
import java.util.Locale

/** Called by one bounded upload worker. No path outside the selected SAF tree is addressable. */
class WifiMusicReceiver(context: Context, private val tree: Uri) {
    private val resolver = context.applicationContext.contentResolver
    init {
        require(tree.authority == "com.android.externalstorage.documents" && MediaStoreAudioFolder.fromTreeUri(tree) != null) { "local_directory_required" }
    }

    fun receive(path: String, length: Long, input: InputStream): String {
        require(length in 1..EchoWifiTransferPolicy.MaxFileBytes)
        val parts = EchoWifiTransferPolicy.pathSegments(path)
        val originalName = parts.last()
        val mime = LocalAudioFileTypes.mimeTypeForFileName(originalName) ?: when (originalName.substringAfterLast('.').lowercase(Locale.ROOT)) {
            "cue", "lrc", "spl", "ttml" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> throw IllegalArgumentException("unsupported_file")
        }
        var parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        parts.dropLast(1).forEach { segment ->
            val existing = children(parent).firstOrNull { it.name == segment }
            parent = if (existing != null) {
                require(existing.directory) { "directory_conflict" }
                existing.uri
            } else checkNotNull(DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, segment))
        }
        val names = children(parent).mapTo(HashSet()) { it.name.lowercase(Locale.ROOT) }
        require(originalName.lowercase(Locale.ROOT) !in names) { "name_conflict" }
        val name = originalName
        val document = checkNotNull(DocumentsContract.createDocument(resolver, parent, mime, name))
        var complete = false
        try {
            checkNotNull(resolver.openOutputStream(document, "w")).use { output ->
                val buffer = ByteArray(64 * 1024)
                var remaining = length
                while (remaining > 0) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedIOException("cancelled")
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) throw EOFException("incomplete_upload")
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    remaining -= read
                }
                output.flush()
            }
            complete = true
            return (parts.dropLast(1) + name).joinToString("/")
        } finally {
            if (!complete) runCatching { DocumentsContract.deleteDocument(resolver, document) }
        }
    }

    fun directoryName(): String {
        check(resolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission && it.isWritePermission }) { "directory_permission_lost" }
        return checkNotNull(resolver.query(
            DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_FLAGS), null, null, null,
        )).use {
            check(it.moveToFirst() && it.getInt(1) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0) { "directory_read_only" }
            it.getString(0)
        }
    }

    private data class Child(val uri: Uri, val name: String, val directory: Boolean)
    private fun children(parent: Uri): List<Child> {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("cancelled")
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        return checkNotNull(resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    check(size < 10_000) { "directory_too_large" }
                    add(Child(DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)), cursor.getString(1),
                        cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR))
                }
            }
        }
    }
}
