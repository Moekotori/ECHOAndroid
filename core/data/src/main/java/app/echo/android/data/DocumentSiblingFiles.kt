package app.echo.android.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

/** Bounded sibling lookup within an existing external-storage tree grant. Call on an I/O worker. */
object DocumentSiblingFiles {
    data class Entry(val name: String, val uri: Uri)
    data class Listing(val files: List<Entry>, val complete: Boolean)

    fun directoryKey(audio: Uri): String? {
        if (audio.authority != "com.android.externalstorage.documents" || !DocumentsContract.isTreeUri(audio)) return null
        val root = DocumentsContract.getTreeDocumentId(audio)
        val parent = parentId(root, DocumentsContract.getDocumentId(audio)) ?: return null
        return "$root\u0000$parent"
    }

    fun read(resolver: ContentResolver, audio: Uri, extensions: Collection<String>, checkActive: () -> Unit = {}): Listing? {
        if (audio.authority != "com.android.externalstorage.documents" || !DocumentsContract.isTreeUri(audio)) return null
        val parent = parentId(DocumentsContract.getTreeDocumentId(audio), DocumentsContract.getDocumentId(audio)) ?: return null
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(audio, parent)
        val files = ArrayList<Entry>()
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
            var visited = 0
            while (cursor.moveToNext()) {
                checkActive()
                if (++visited > 4096) return Listing(files, false)
                if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                val name = cursor.getString(1) ?: continue
                if (extensions.none { name.endsWith(it, ignoreCase = true) }) continue
                files += Entry(name, DocumentsContract.buildDocumentUriUsingTree(audio, cursor.getString(0)))
            }
            return Listing(files, true)
        }
        return null
    }

    internal fun parentId(root: String, document: String): String? {
        if (document.substringAfter(':').split('/').any { it == "." || it == ".." }) return null
        if (document == root || !document.startsWith(if (root.endsWith(':')) root else "$root/")) return null
        val parent = if ('/' in document) document.substringBeforeLast('/') else document.substringBefore(':') + ":"
        return parent.takeIf { it == root || it.startsWith(if (root.endsWith(':')) root else "$root/") }
    }
}
