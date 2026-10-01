package app.echo.android.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import app.echo.android.model.library.CueSheetPolicy

/** Resolve file names in batches; MediaStore URI tails are IDs, not file names. Called on IO. */
internal suspend fun resolveLibraryFileNames(resolver: ContentResolver, rows: List<M3uMatchRow>): List<M3uMatchRow> {
    val names = HashMap<String, String>()
    val mediaRows = ArrayList<M3uMatchRow>()
    for (row in rows) {
        coroutineContext.ensureActive()
        val uri = Uri.parse(CueSheetPolicy.playbackUri(row.contentUri))
        when {
            row.fileName != null -> names[row.id] = row.fileName
            uri.authority == MediaStore.AUTHORITY -> mediaRows += row
            else -> {
                val name = when {
                    uri.authority == "com.android.externalstorage.documents" ->
                        runCatching { DocumentsContract.getDocumentId(uri).substringAfter(':').substringAfterLast('/') }.getOrNull()
                    uri.scheme == "file" -> uri.path?.substringAfterLast('/')
                    else -> runCatching {
                        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                    }.getOrNull()
                }
                name?.takeIf { it.isNotBlank() }?.let { names[row.id] = it }
            }
        }
    }
    for ((collection, group) in mediaRows.groupBy { CueSheetPolicy.playbackUri(it.contentUri).substringBeforeLast('/') }) {
        val byId = group.groupBy { CueSheetPolicy.playbackUri(it.contentUri).substringAfterLast('/') }
        for (chunk in byId.keys.chunked(500)) {
            coroutineContext.ensureActive()
            runCatching {
                resolver.query(Uri.parse(collection), arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                    "_id IN (${chunk.joinToString(",") { "?" }})", chunk.toTypedArray(), null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(1) ?: continue
                        byId[cursor.getLong(0).toString()].orEmpty().forEach { names[it.id] = name }
                    }
                }
            }
        }
    }
    return rows.map { it.copy(fileName = names[it.id]) }
}
