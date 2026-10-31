package app.echo.android.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.echo.android.model.connect.EchoSongTransferPolicy
import java.io.InputStream

data class EchoLinkSongFile(val uri: Uri, val name: String, val size: Long)

/** Queries run on an I/O dispatcher. The picker grants access to only the selected files. */
class EchoLinkSongFiles(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    fun describe(uri: Uri): EchoLinkSongFile {
        require(uri.scheme == "content") { "invalid_file" }
        return checkNotNull(resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)).use { cursor ->
            check(cursor.moveToFirst()) { "invalid_file" }
            val name = cursor.getString(0); val size = if (cursor.isNull(1)) -1 else cursor.getLong(1)
            require(EchoSongTransferPolicy.validFile(name, size)) { "unsupported_file" }
            EchoLinkSongFile(uri, name, size)
        }
    }
    fun open(file: EchoLinkSongFile): InputStream = checkNotNull(resolver.openInputStream(file.uri))
}
