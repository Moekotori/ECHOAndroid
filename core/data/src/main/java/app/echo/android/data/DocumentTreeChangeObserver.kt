package app.echo.android.data

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn

/** Some providers only send notifications for changes made through their own APIs. */
fun observeDocumentTreeChanges(resolver: ContentResolver, trees: List<String>): Flow<Unit> = callbackFlow {
    val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            trySend(Unit)
        }
    }
    try {
        trees.forEach { raw ->
            val tree = Uri.parse(raw)
            val authority = tree.authority ?: return@forEach
            val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return@forEach
            listOf(
                DocumentsContract.buildChildDocumentsUriUsingTree(tree, id),
                DocumentsContract.buildChildDocumentsUri(authority, id),
            ).forEach { children ->
                runCatching { resolver.registerContentObserver(children, true, observer) }
            }
        }
        awaitClose()
    } finally {
        runCatching { resolver.unregisterContentObserver(observer) }
    }
}.conflate().flowOn(Dispatchers.IO)
