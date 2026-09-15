package app.echo.android.data

import org.json.JSONArray
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class DocumentTreeCachedChild(
    val documentId: String,
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
)

/** Worker-thread cache of successful SAF directory listings keyed by tree + document id. */
class DocumentTreeListingCache(
    private val file: File? = null,
    private val capacity: Int = DefaultCapacity,
) {
    private data class Entry(
        val key: String,
        val lastModifiedMs: Long,
        val children: List<DocumentTreeCachedChild>,
    )

    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private var loaded = false
    private var dirty = false

    fun listing(
        treeUri: String,
        documentId: String,
        lastModifiedMs: Long,
        isTreeRoot: Boolean,
    ): List<DocumentTreeCachedChild>? {
        load()
        val key = cacheKey(treeUri, documentId) ?: return null
        val entry = entries[key] ?: return null
        if (!LibraryScanPolicy.shouldReuseCachedDocumentListing(entry.lastModifiedMs, lastModifiedMs, isTreeRoot)) {
            entries.remove(key)
            dirty = true
            return null
        }
        return entry.children
    }

    fun remember(
        treeUri: String,
        documentId: String,
        lastModifiedMs: Long,
        isTreeRoot: Boolean,
        children: List<DocumentTreeCachedChild>,
    ) {
        load()
        if (isTreeRoot || lastModifiedMs <= 0L || capacity <= 0) return
        if (children.size > MaxChildrenPerDirectory) return
        val key = cacheKey(treeUri, documentId) ?: return
        if (children.any { !it.isPersistable() }) return
        val entry = Entry(key, lastModifiedMs, children)
        if (entries[key] == entry) return
        entries[key] = entry
        while (entries.size > capacity) entries.remove(entries.keys.first())
        dirty = true
    }

    fun flush() {
        val target = file ?: return
        if (!dirty) return
        runCatching {
            var bytes = 0
            val recent = entries.values.toList().asReversed().takeWhile { entry ->
                bytes += entry.encodedSize()
                bytes < MaxFileBytes
            }.asReversed()
            val data = JSONArray()
            recent.forEach { entry ->
                val children = JSONArray()
                entry.children.forEach { child ->
                    children.put(
                        JSONArray()
                            .put(child.documentId)
                            .put(child.name)
                            .put(child.mimeType.orEmpty())
                            .put(child.sizeBytes)
                            .put(child.lastModifiedMs),
                    )
                }
                data.put(JSONArray().put(entry.key).put(entry.lastModifiedMs).put(children))
            }
            target.parentFile?.mkdirs()
            val temp = File(target.path + ".tmp")
            temp.writeText(data.toString())
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            dirty = false
        }
    }

    private fun load() {
        if (loaded) return
        loaded = true
        val source = file ?: return
        if (!source.isFile || source.length() > MaxFileBytes) return
        runCatching {
            val rows = JSONArray(source.readText())
            for (i in maxOf(0, rows.length() - capacity.coerceAtLeast(0)) until rows.length()) {
                val row = rows.getJSONArray(i)
                val key = row.getString(0)
                val lastModifiedMs = row.getLong(1)
                val childrenJson = row.getJSONArray(2)
                if (key.length > MaxKeyLength || lastModifiedMs <= 0L) continue
                val children = ArrayList<DocumentTreeCachedChild>(childrenJson.length())
                var persistable = true
                for (childIndex in 0 until childrenJson.length()) {
                    val childRow = childrenJson.getJSONArray(childIndex)
                    val child = DocumentTreeCachedChild(
                        documentId = childRow.getString(0),
                        name = childRow.getString(1),
                        mimeType = childRow.getString(2).takeIf { it.isNotEmpty() },
                        sizeBytes = childRow.getLong(3),
                        lastModifiedMs = childRow.getLong(4),
                    )
                    if (!child.isPersistable()) {
                        persistable = false
                        break
                    }
                    children += child
                }
                if (persistable && children.size <= MaxChildrenPerDirectory) {
                    entries[key] = Entry(key, lastModifiedMs, children)
                }
            }
        }.onFailure { entries.clear() }
    }

    private companion object {
        const val DefaultCapacity = 2_000
        const val MaxFileBytes = 4 * 1024 * 1024
        const val MaxKeyLength = 2_048
        const val MaxChildrenPerDirectory = 2_000
        const val MaxChildIdLength = 1_024
        const val MaxChildNameLength = 512
        const val MaxMimeLength = 128

        fun cacheKey(treeUri: String, documentId: String): String? {
            val key = "$treeUri\n$documentId"
            return key.takeIf { it.length <= MaxKeyLength && treeUri.isNotBlank() && documentId.isNotBlank() }
        }

        fun DocumentTreeCachedChild.isPersistable(): Boolean =
            documentId.isNotBlank() &&
                documentId.length <= MaxChildIdLength &&
                name.length <= MaxChildNameLength &&
                (mimeType == null || mimeType.length <= MaxMimeLength)

        fun Entry.encodedSize(): Int =
            key.length + 32 + children.sumOf { it.documentId.length + it.name.length + 48 }
    }
}
