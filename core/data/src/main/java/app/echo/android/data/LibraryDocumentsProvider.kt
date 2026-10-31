package app.echo.android.data

import android.content.ContentResolver
import android.database.CrossProcessCursorWrapper
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import java.io.FileNotFoundException

/** SAF transport independent of Room wiring; directories are for browsing, never tree grants. */
abstract class LibraryDocumentsProvider : DocumentsProvider() {
    protected abstract fun queryDatabase(sql: String, args: Array<String>, signal: CancellationSignal? = null): Cursor
    private val authority get() = "${requireNotNull(context).packageName}.library-documents"
    private val changeUri get() = Uri.Builder().scheme("content").authority(authority).build()
    private val directoryFlags get() = if (Build.VERSION.SDK_INT >= 30) DocumentsContract.Document.FLAG_DIR_BLOCKS_OPEN_DOCUMENT_TREE else 0
    private val unknown get() = requireNotNull(context).getString(R.string.documents_unknown_album)
    private val whole get() = requireNotNull(context).getString(R.string.documents_whole_original)
    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val columns = projection ?: arrayOf("root_id", "document_id", "title", "summary", "flags", "icon", "mime_types", "query_args")
        return matrix(columns, mapOf(
            "root_id" to LibraryDocumentsQueries.Root, "document_id" to LibraryDocumentsQueries.Root,
            "title" to requireNotNull(context).getString(R.string.documents_root),
            "summary" to requireNotNull(context).getString(R.string.documents_root_summary),
            "flags" to (DocumentsContract.Root.FLAG_LOCAL_ONLY or DocumentsContract.Root.FLAG_SUPPORTS_SEARCH),
            "icon" to requireNotNull(context).applicationInfo.icon, "mime_types" to "audio/*",
            "query_args" to if (Build.VERSION.SDK_INT >= 29) DocumentsContract.QUERY_ARG_DISPLAY_NAME else null,
        )).apply { setNotificationUri(requireNotNull(context).contentResolver, DocumentsContract.buildRootsUri(authority)) }
    }

    private fun title(id: String): String? = when (id) {
        LibraryDocumentsQueries.Root -> requireNotNull(context).getString(R.string.documents_root)
        LibraryDocumentsQueries.Albums -> requireNotNull(context).getString(R.string.documents_albums)
        LibraryDocumentsQueries.Playlists -> requireNotNull(context).getString(R.string.documents_playlists)
        LibraryDocumentsQueries.All -> requireNotNull(context).getString(R.string.documents_all)
        else -> null
    }

    private fun directory(id: String): Map<String, Any?> = mapOf("document_id" to id, "_display_name" to title(id),
        "mime_type" to DocumentsContract.Document.MIME_TYPE_DIR, "flags" to directoryFlags,
        "summary" to if (id == LibraryDocumentsQueries.Playlists) requireNotNull(context).getString(R.string.documents_playlists_summary) else null)

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        if (title(documentId) != null) return matrix(projection ?: LibraryDocumentsQueries.Columns, directory(documentId))
        val plan = LibraryDocumentsQueries.document(documentId, unknown, whole, directoryFlags) ?: throw FileNotFoundException()
        return execute(LibraryDocumentsQueries.project(plan, projection))
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        children(parentDocumentId, projection, LibraryDocumentsQueries.sort(sortOrder), null)

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, queryArgs: Bundle?): Cursor {
        val column = queryArgs?.getStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS)?.singleOrNull()
        val direction = if (queryArgs?.getInt(ContentResolver.QUERY_ARG_SORT_DIRECTION) == ContentResolver.QUERY_SORT_DIRECTION_DESCENDING) "DESC" else "ASC"
        return children(parentDocumentId, projection, LibraryDocumentsQueries.sort(column?.let { "$it $direction" }), queryArgs)
    }

    private fun children(parent: String, projection: Array<out String>?, order: String?, args: Bundle?): Cursor {
        if (parent == LibraryDocumentsQueries.Root) {
            val result = MatrixCursor(projection ?: LibraryDocumentsQueries.Columns)
            listOf(LibraryDocumentsQueries.Albums, LibraryDocumentsQueries.Playlists, LibraryDocumentsQueries.All).forEach { id ->
                result.addRow(result.columnNames.map { directory(id)[it] }.toTypedArray())
            }
            result.setNotificationUri(requireNotNull(context).contentResolver, changeUri)
            return result
        }
        val plan = LibraryDocumentsQueries.children(parent, unknown, whole, directoryFlags) ?: throw FileNotFoundException()
        val limit = args?.takeIf { it.containsKey(ContentResolver.QUERY_ARG_LIMIT) }?.getInt(ContentResolver.QUERY_ARG_LIMIT)?.takeIf { it > 0 }
        val offset = args?.getInt(ContentResolver.QUERY_ARG_OFFSET)?.coerceAtLeast(0) ?: 0
        val honored = mutableListOf<String>()
        if (limit != null) honored += ContentResolver.QUERY_ARG_LIMIT
        if (args?.containsKey(ContentResolver.QUERY_ARG_OFFSET) == true) honored += ContentResolver.QUERY_ARG_OFFSET
        if (order != null && args != null) { honored += ContentResolver.QUERY_ARG_SORT_COLUMNS; honored += ContentResolver.QUERY_ARG_SORT_DIRECTION }
        return execute(LibraryDocumentsQueries.project(plan, projection, order ?: plan.order, limit, offset),
            Bundle().apply { putStringArray(ContentResolver.EXTRA_HONORED_ARGS, honored.toTypedArray()) })
    }

    override fun querySearchDocuments(rootId: String, query: String, projection: Array<out String>?): Cursor {
        if (rootId != LibraryDocumentsQueries.Root) throw FileNotFoundException()
        return execute(LibraryDocumentsQueries.project(LibraryDocumentsQueries.search(query, whole), projection))
    }

    @RequiresApi(29)
    override fun querySearchDocuments(rootId: String, projection: Array<out String>?, queryArgs: Bundle): Cursor {
        if (rootId != LibraryDocumentsQueries.Root) throw FileNotFoundException()
        val query = queryArgs.getString(DocumentsContract.QUERY_ARG_DISPLAY_NAME)
            ?: return MatrixCursor(projection ?: LibraryDocumentsQueries.Columns)
        return execute(LibraryDocumentsQueries.project(LibraryDocumentsQueries.search(query, whole), projection),
            Bundle().apply { putStringArray(ContentResolver.EXTRA_HONORED_ARGS, arrayOf(DocumentsContract.QUERY_ARG_DISPLAY_NAME)) })
    }

    private fun execute(plan: LibraryDocumentsSql, extras: Bundle = Bundle()): Cursor = try {
        val cursor = queryDatabase(plan.sql, plan.args.toTypedArray())
        try { cursor.setNotificationUri(requireNotNull(context).contentResolver, changeUri) }
        catch (failure: Exception) { cursor.close(); throw failure }
        object : CrossProcessCursorWrapper(cursor) { override fun getExtras(): Bundle = extras }
    } catch (_: SQLiteException) {
        MatrixCursor(LibraryDocumentsQueries.Columns).apply {
            setExtras(Bundle().apply { putString(DocumentsContract.EXTRA_ERROR, requireNotNull(context).getString(R.string.documents_unavailable)) })
        }
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read only")
        signal?.throwIfCanceled()
        val plan = LibraryDocumentsQueries.source(documentId) ?: throw FileNotFoundException()
        val source = queryDatabase(plan.sql, plan.args.toTypedArray(), signal).use { cursor ->
            if (!cursor.moveToFirst()) throw FileNotFoundException()
            cursor.getString(0).substringBefore('#').toUri()
        }
        if (source.authority == authority) throw FileNotFoundException()
        try { return LocalAudioFileAccess.open(requireNotNull(context), source, signal) }
        catch (_: SecurityException) { throw FileNotFoundException(requireNotNull(context).getString(R.string.documents_unavailable)) }
    }

    // No directory-wide delegation. The system grants access only to selected document URIs.
    override fun isChildDocument(parentDocumentId: String, documentId: String) = false

    private fun matrix(columns: Array<out String>, values: Map<String, Any?>): MatrixCursor =
        MatrixCursor(columns).apply { addRow(columns.map { values[it] }.toTypedArray()) }
}
