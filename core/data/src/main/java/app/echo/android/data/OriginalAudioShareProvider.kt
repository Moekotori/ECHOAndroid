package app.echo.android.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.net.toUri
import java.io.File
import java.io.FileNotFoundException

/** Exact, granted file URIs survive process recreation without copying large audio files. */
class OriginalAudioShareProvider : ContentProvider() {
    override fun onCreate() = true
    private val store by lazy { OriginalAudioShareStore(File(requireNotNull(context).cacheDir, "original-audio-shares")) }

    private fun entry(uri: Uri): OriginalAudioShareEntry {
        val parts = uri.pathSegments
        if (uri.authority != "${requireNotNull(context).packageName}.audio-share" || parts.size != 3) throw FileNotFoundException()
        return store.entry(parts[0], parts[1].toIntOrNull() ?: throw FileNotFoundException(), parts[2])
    }

    override fun getType(uri: Uri): String? = try { entry(uri).mime } catch (_: FileNotFoundException) { null }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val file = entry(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(columns.map<String, Any?> { when (it) {
                OpenableColumns.DISPLAY_NAME -> file.name
                OpenableColumns.SIZE -> file.size.takeIf { size -> size >= 0 }
                else -> null
            } }.toTypedArray())
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = openFile(uri, mode, null)

    override fun openFile(uri: Uri, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read only")
        signal?.throwIfCanceled()
        val source = entry(uri).source.toUri()
        return LocalAudioFileAccess.open(requireNotNull(context), source, signal)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
