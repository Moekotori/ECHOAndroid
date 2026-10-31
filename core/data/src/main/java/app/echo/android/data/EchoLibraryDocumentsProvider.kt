package app.echo.android.data

import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SimpleSQLiteQuery

/** Room remains the library owner; its invalidation refreshes an already-open system picker. */
class EchoLibraryDocumentsProvider : LibraryDocumentsProvider() {
    private val observer = object : InvalidationTracker.Observer(
        "library_tracks", "library_hidden_tracks", "library_playlists", "library_playlist_tracks", "library_smart_rules",
    ) {
        override fun onInvalidated(tables: Set<String>) {
            val app = context ?: return
            val authority = "${app.packageName}.library-documents"
            app.contentResolver.notifyChange(Uri.Builder().scheme("content").authority(authority).build(), null)
            app.contentResolver.notifyChange(DocumentsContract.buildRootsUri(authority), null)
        }
    }
    private val library = lazy {
        EchoLibraryDatabase.create(requireNotNull(context)).also { it.invalidationTracker.addObserver(observer) }
    }

    override fun queryDatabase(sql: String, args: Array<String>, signal: CancellationSignal?): Cursor =
        library.value.openHelper.readableDatabase.query(SimpleSQLiteQuery(sql, args), signal)

    override fun shutdown() {
        if (library.isInitialized()) library.value.invalidationTracker.removeObserver(observer)
        super.shutdown()
    }
}
