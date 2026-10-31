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
import java.util.Locale

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
        val columns = LibraryDocumentsQueries.columns(projection, arrayOf("root_id", "document_id", "title", "summary", "flags", "icon", "mime_types", "query_args"))
        return matrix(columns, mapOf(
            "root_id" to LibraryDocumentsQueries.Root, "document_id" to LibraryDocumentsQueries.Root,
            "title" to requireNotNull(context).getString(R.string.documents_root),
            "summary" to requireNotNull(context).getString(R.string.documents_root_summary),
            "flags" to (DocumentsContract.Root.FLAG_LOCAL_ONLY or DocumentsContract.Root.FLAG_SUPPORTS_SEARCH),
            "icon" to requireNotNull(context).applicationInfo.icon, "mime_types" to "audio/*",
            "query_args" to if (Build.VERSION.SDK_INT >= 29) listOf(DocumentsContract.QUERY_ARG_DISPLAY_NAME,
                DocumentsContract.QUERY_ARG_MIME_TYPES, DocumentsContract.QUERY_ARG_FILE_SIZE_OVER,
                DocumentsContract.QUERY_ARG_LAST_MODIFIED_AFTER).joinToString("\n") else null,
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
        if (title(documentId) != null) return matrix(LibraryDocumentsQueries.columns(projection), directory(documentId))
        val plan = LibraryDocumentsQueries.document(documentId, unknown, whole, directoryFlags) ?: throw FileNotFoundException()
        return execute(LibraryDocumentsQueries.project(plan, projection), projection = projection)
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        children(parentDocumentId, projection, LibraryDocumentsQueries.sort(sortOrder), null)

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, queryArgs: Bundle?): Cursor {
        val column = queryArgs?.getStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS)?.singleOrNull()
        val direction = queryArgs?.getInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_ASCENDING)
        val structured = if (direction == ContentResolver.QUERY_SORT_DIRECTION_ASCENDING || direction == ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            LibraryDocumentsQueries.sort(column?.let { "$it ${if (direction == ContentResolver.QUERY_SORT_DIRECTION_DESCENDING) "DESC" else "ASC"}" }) else null
        val legacy = LibraryDocumentsQueries.sort(queryArgs?.getString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER))
        val honored = when {
            structured != null -> listOf(ContentResolver.QUERY_ARG_SORT_COLUMNS) +
                if (queryArgs?.containsKey(ContentResolver.QUERY_ARG_SORT_DIRECTION) == true) listOf(ContentResolver.QUERY_ARG_SORT_DIRECTION) else emptyList()
            legacy != null -> listOf(ContentResolver.QUERY_ARG_SQL_SORT_ORDER)
            else -> emptyList()
        }
        return children(parentDocumentId, projection, structured ?: legacy, queryArgs, honored)
    }

    private fun children(parent: String, projection: Array<out String>?, order: String?, args: Bundle?, honoredSort: List<String> = emptyList()): Cursor {
        val limit = args?.takeIf { it.containsKey(ContentResolver.QUERY_ARG_LIMIT) }?.getInt(ContentResolver.QUERY_ARG_LIMIT)?.takeIf { it >= 0 }
        val requestedOffset = args?.takeIf { it.containsKey(ContentResolver.QUERY_ARG_OFFSET) }?.getInt(ContentResolver.QUERY_ARG_OFFSET)
        val offset = requestedOffset?.coerceAtLeast(0) ?: 0
        val honored = honoredSort.toMutableList()
        if (limit != null) honored += ContentResolver.QUERY_ARG_LIMIT
        if (requestedOffset != null && requestedOffset >= 0) honored += ContentResolver.QUERY_ARG_OFFSET
        val extras = Bundle().apply { putStringArray(ContentResolver.EXTRA_HONORED_ARGS, honored.toTypedArray()) }
        if (parent == LibraryDocumentsQueries.Root) {
            val result = MatrixCursor(LibraryDocumentsQueries.columns(projection))
            var ids = listOf(LibraryDocumentsQueries.Albums, LibraryDocumentsQueries.Playlists, LibraryDocumentsQueries.All)
            if (order != null) ids = if (order.startsWith("_display_name")) {
                val compare = if (order.contains(" DESC,")) compareByDescending<String> { title(it)?.lowercase(Locale.ROOT) }
                    else compareBy<String> { title(it)?.lowercase(Locale.ROOT) }
                ids.sortedWith(compare.thenBy { it })
            } else ids.sorted()
            ids.drop(offset).let { if (limit == null) it else it.take(limit) }.forEach { id ->
                result.addRow(result.columnNames.map { directory(id)[it] }.toTypedArray())
            }
            result.setExtras(extras)
            result.setNotificationUri(requireNotNull(context).contentResolver, changeUri)
            return result
        }
        val plan = LibraryDocumentsQueries.children(parent, unknown, whole, directoryFlags) ?: throw FileNotFoundException()
        return execute(LibraryDocumentsQueries.project(plan, projection, order ?: plan.order, limit, offset), extras, projection)
    }

    override fun querySearchDocuments(rootId: String, query: String, projection: Array<out String>?): Cursor {
        if (rootId != LibraryDocumentsQueries.Root) throw FileNotFoundException()
        return execute(LibraryDocumentsQueries.project(LibraryDocumentsQueries.search(query, whole), projection), projection = projection)
    }

    @RequiresApi(29)
    override fun querySearchDocuments(rootId: String, projection: Array<out String>?, queryArgs: Bundle): Cursor {
        if (rootId != LibraryDocumentsQueries.Root) throw FileNotFoundException()
        val name = queryArgs.getString(DocumentsContract.QUERY_ARG_DISPLAY_NAME)
        val types = queryArgs.getStringArray(DocumentsContract.QUERY_ARG_MIME_TYPES)?.toList()
        val size = queryArgs.takeIf { it.containsKey(DocumentsContract.QUERY_ARG_FILE_SIZE_OVER) }?.getLong(DocumentsContract.QUERY_ARG_FILE_SIZE_OVER)
        val modified = queryArgs.takeIf { it.containsKey(DocumentsContract.QUERY_ARG_LAST_MODIFIED_AFTER) }?.getLong(DocumentsContract.QUERY_ARG_LAST_MODIFIED_AFTER)
        val honored = mutableListOf<String>()
        if (name != null) honored += DocumentsContract.QUERY_ARG_DISPLAY_NAME
        if (types != null) honored += DocumentsContract.QUERY_ARG_MIME_TYPES
        if (size != null) honored += DocumentsContract.QUERY_ARG_FILE_SIZE_OVER
        if (modified != null) honored += DocumentsContract.QUERY_ARG_LAST_MODIFIED_AFTER
        val plan = LibraryDocumentsQueries.searchFilters(LibraryDocumentsQueries.search(name.orEmpty(), whole), types, size, modified)
        return execute(LibraryDocumentsQueries.project(plan, projection),
            Bundle().apply { putStringArray(ContentResolver.EXTRA_HONORED_ARGS, honored.toTypedArray()) }, projection)
    }

    private fun execute(plan: LibraryDocumentsSql, extras: Bundle = Bundle(), projection: Array<out String>? = null): Cursor {
        var opened: Cursor? = null
        try {
            val cursor = queryDatabase(plan.sql, plan.args.toTypedArray()).also { opened = it }
            // Force the first window while failures can still become a recoverable picker error.
            cursor.moveToFirst()
            cursor.moveToPosition(-1)
            cursor.setNotificationUri(requireNotNull(context).contentResolver, changeUri)
            return object : CrossProcessCursorWrapper(cursor) { override fun getExtras(): Bundle = extras }
        } catch (failure: Throwable) {
            opened?.close()
            if (failure !is SQLiteException) throw failure
            return MatrixCursor(LibraryDocumentsQueries.columns(projection)).apply {
                setExtras(Bundle().apply { putString(DocumentsContract.EXTRA_ERROR, requireNotNull(context).getString(R.string.documents_unavailable)) })
                setNotificationUri(requireNotNull(context).contentResolver, changeUri)
            }
        }
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read only")
        signal?.throwIfCanceled()
        val plan = LibraryDocumentsQueries.source(documentId) ?: throw FileNotFoundException()
        try {
            val source = queryDatabase(plan.sql, plan.args.toTypedArray(), signal).use { cursor ->
                if (!cursor.moveToFirst()) throw FileNotFoundException()
                cursor.getString(0).substringBefore('#').toUri()
            }
            if (source.authority == authority) throw FileNotFoundException()
            return LocalAudioFileAccess.open(requireNotNull(context), source, signal)
        }
        catch (_: SecurityException) { throw FileNotFoundException(requireNotNull(context).getString(R.string.documents_unavailable)) }
        catch (_: FileNotFoundException) { throw FileNotFoundException(requireNotNull(context).getString(R.string.documents_unavailable)) }
        catch (_: SQLiteException) { throw FileNotFoundException(requireNotNull(context).getString(R.string.documents_unavailable)) }
    }

    // No directory-wide delegation. The system grants access only to selected document URIs.
    override fun isChildDocument(parentDocumentId: String, documentId: String) = false

    private fun matrix(columns: Array<out String>, values: Map<String, Any?>): MatrixCursor =
        MatrixCursor(columns).apply { addRow(columns.map { values[it] }.toTypedArray()) }
}
