package app.echo.android.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.echo.android.model.connect.EchoWifiTransferPolicy
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.IOException
import java.io.FileNotFoundException
import java.util.UUID
import java.util.Locale

/** Called by one bounded upload worker. No path outside the selected SAF tree is addressable. */
class WifiMusicReceiver(context: Context, private val tree: Uri) {
    private companion object { val UploadLock = Any() }
    private val application = context.applicationContext
    private val resolver = application.contentResolver
    private val transaction by lazy { WifiUploadTransaction(WifiUploadPreferencesJournal(application), uploadFiles) }
    init {
        require(tree.authority == "com.android.externalstorage.documents" && MediaStoreAudioFolder.fromTreeUri(tree) != null) { "local_directory_required" }
    }

    fun recoverPendingTransfers() = synchronized(UploadLock) { transaction.recover(tree.toString()) }

    fun receive(path: String, length: Long, input: InputStream): String = synchronized(UploadLock) {
        require(length in 1..EchoWifiTransferPolicy.MaxFileBytes)
        transaction.recover(tree.toString())
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
        require(names.size < 10_000) { "directory_too_large" }
        require(originalName.lowercase(Locale.ROOT) !in names) { "name_conflict" }
        val id = UUID.randomUUID().toString()
        transaction.receive(PendingWifiUpload(id, tree.toString(), parent.toString(), ".echo-upload-$id.part", originalName, mime), length, input)
        parts.joinToString("/")
    }

    private val uploadFiles = object : WifiUploadFiles {
        override fun create(upload: PendingWifiUpload): String = checkNotNull(DocumentsContract.createDocument(
            resolver, Uri.parse(upload.parent), "application/octet-stream", upload.temporaryName)).toString()

        override fun open(document: String) = checkNotNull(resolver.openOutputStream(Uri.parse(document), "w"))

        override fun publish(upload: PendingWifiUpload) {
            val parent = Uri.parse(upload.parent)
            require(children(parent).none { it.name.equals(upload.finalName, ignoreCase = true) }) { "name_conflict" }
            val renamed = DocumentsContract.renameDocument(resolver, Uri.parse(checkNotNull(upload.document)), upload.finalName)
                ?: throw IOException("publish_failed")
            val actualName = resolver.query(renamed, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
            if (actualName != upload.finalName) {
                // The provider may rename a collision; never leave CUE references pointing at a different file.
                runCatching { DocumentsContract.deleteDocument(resolver, renamed) }
                throw IOException("publish_failed")
            }
        }

        override fun deleteTemporary(upload: PendingWifiUpload): Boolean {
            val parent = Uri.parse(upload.parent)
            fun owned(name: String) = name == upload.temporaryName || name.startsWith(upload.temporaryName + ".")
            upload.document?.let { value ->
                val uri = Uri.parse(value)
                fun current(): Child? = try {
                    checkNotNull(resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)).use {
                        if (it.moveToFirst()) Child(uri, it.getString(0), it.getString(1) == DocumentsContract.Document.MIME_TYPE_DIR) else null
                    }
                } catch (_: FileNotFoundException) { null }
                val file = current() ?: return true
                if (!owned(file.name)) return true // A completed rename may keep a stable document ID.
                if (file.directory || !DocumentsContract.deleteDocument(resolver, uri)) return false
                return current()?.let { !owned(it.name) } ?: true
            }
            // No URI was recorded: recover the small gap between createDocument and journal update.
            val pending = children(parent).filter { owned(it.name) }
            var removed = true
            pending.forEach {
                if (it.directory || !DocumentsContract.deleteDocument(resolver, it.uri)) removed = false
            }
            // A renamed, complete file is deliberately outside this predicate, including after process death.
            return removed && children(parent).none { owned(it.name) }
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
