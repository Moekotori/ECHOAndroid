package app.echo.android.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.echo.android.model.connect.EchoSongTransferPolicy
import java.io.FilterInputStream
import java.io.InputStream
import java.util.Locale

/** Reuses the existing bounded SAF writer and its partial-file cleanup. */
class EchoLinkSongReceiver(context: Context, private val tree: Uri) {
    private val resolver = context.applicationContext.contentResolver
    private val receiver = WifiMusicReceiver(context.applicationContext, tree)
    fun directoryName(): String = receiver.directoryName()
    fun receive(name: String, size: Long, input: InputStream, checkActive: () -> Unit, progress: (Long) -> Unit): String {
        require(EchoSongTransferPolicy.validFile(name, size)) { "unsupported_file" }
        checkActive()
        val names = HashSet<String>()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        checkNotNull(resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)).use { cursor ->
            while (cursor.moveToNext()) {
                checkActive(); require(names.size < 10000) { "directory_too_large" }
                names.add(cursor.getString(0).lowercase(Locale.ROOT))
            }
        }
        val extension = name.substringAfterLast('.')
        val stem = name.substringBeforeLast('.').replace(Regex("[:*?\"<>|]"), "_").trim(' ', '.').take(100).ifBlank { "Song" }
        var target = "$stem.$extension"; var suffix = 1
        while (target.lowercase(Locale.ROOT) in names && suffix <= 10000) target = "$stem (${suffix++}).$extension"
        require(target.lowercase(Locale.ROOT) !in names) { "name_conflict" }
        var copied = 0L
        val checked = object : FilterInputStream(input) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                checkActive()
                val count = super.read(bytes, offset, length)
                checkActive()
                if (count > 0) { copied += count; progress(copied) }
                return count
            }
        }
        checkActive()
        return receiver.receive(target, size, checked)
    }
}
