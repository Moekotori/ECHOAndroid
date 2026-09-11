package app.echo.android.data

import app.echo.android.model.library.LibraryScanOptions
import org.json.JSONArray
import org.json.JSONObject

data class WatchedLibraryTree(
    val uri: String,
    val documentId: String,
    val lastScanEpochMs: Long = 0L,
    val minDurationMs: Long = LibraryScanOptions().minDurationMs,
    val minSizeBytes: Long = LibraryScanOptions().minSizeBytes,
    val excludeNonMusicFolders: Boolean = LibraryScanOptions().excludeNonMusicFolders,
    val excludeHiddenFolders: Boolean = LibraryScanOptions().excludeHiddenFolders,
) {
    fun scanOptions(): LibraryScanOptions =
        LibraryScanOptions(
            minDurationMs = minDurationMs,
            minSizeBytes = minSizeBytes,
            excludeNonMusicFolders = excludeNonMusicFolders,
            excludeHiddenFolders = excludeHiddenFolders,
        )
}

object LibraryFolderWatchPolicy {
    const val MaxTrees = 8
    const val ForegroundDebounceMs = 1_200L
    const val AutoRescanMinIntervalMs = 30_000L
    const val LightweightAutoRescanMinIntervalMs = 120_000L

    fun autoRescanMinIntervalMs(lightweight: Boolean): Long =
        if (lightweight) LightweightAutoRescanMinIntervalMs else AutoRescanMinIntervalMs

    fun normalizeDocumentId(documentId: String): String {
        val (volume, path) = LibraryScanPolicy.splitDocumentTreeId(documentId) ?: return documentId.trim()
        val vol = volume.lowercase()
        return if (path.isEmpty()) vol else "$vol:$path"
    }

    fun documentIdCovers(parentDocumentId: String, childDocumentId: String): Boolean {
        val parent = normalizeDocumentId(parentDocumentId)
        val child = normalizeDocumentId(childDocumentId)
        if (parent.isBlank() || child.isBlank()) return false
        if (parent == child) return true
        if (child.startsWith("$parent/")) return true
        return !parent.contains(':') && child.startsWith("$parent:")
    }

    fun remember(
        existing: List<WatchedLibraryTree>,
        incoming: WatchedLibraryTree,
        maxTrees: Int = MaxTrees,
    ): List<WatchedLibraryTree> {
        val uri = incoming.uri.trim()
        val documentId = normalizeDocumentId(incoming.documentId)
        if (uri.isBlank() || documentId.isBlank()) return existing
        val nextIncoming = incoming.copy(uri = uri, documentId = documentId)
        val parent = existing.firstOrNull { tree ->
            tree.uri != uri && documentIdCovers(tree.documentId, documentId)
        }
        if (parent != null) {
            return existing.map { tree ->
                if (tree.uri == parent.uri) {
                    tree.copy(lastScanEpochMs = maxOf(tree.lastScanEpochMs, nextIncoming.lastScanEpochMs))
                } else {
                    tree
                }
            }
        }
        val withoutCovered = existing.filterNot { tree ->
            tree.uri == uri || documentIdCovers(documentId, tree.documentId)
        }
        val merged = withoutCovered + nextIncoming
        if (merged.size <= maxTrees) return merged
        val drop = merged.size - maxTrees
        val dropUris = merged.asSequence()
            .filter { it.uri != uri }
            .sortedBy { it.lastScanEpochMs }
            .take(drop)
            .map { it.uri }
            .toSet()
        return merged.filterNot { it.uri in dropUris }
    }

    fun pruneRevoked(
        trees: List<WatchedLibraryTree>,
        grantedUris: Set<String>,
    ): List<WatchedLibraryTree> {
        if (grantedUris.isEmpty()) return emptyList()
        val grantedCanonical = grantedUris.map(::canonicalUri).toSet()
        return trees.filter { tree ->
            val treeUri = canonicalUri(tree.uri)
            treeUri in grantedCanonical ||
                grantedCanonical.any { granted -> uriCovers(granted, treeUri) }
        }
    }

    fun treesDueForAutoScan(
        trees: List<WatchedLibraryTree>,
        nowEpochMs: Long,
        storageBusy: Boolean,
        lightweight: Boolean,
        enabled: Boolean,
    ): List<WatchedLibraryTree> {
        if (!enabled || storageBusy || trees.isEmpty()) return emptyList()
        val minInterval = autoRescanMinIntervalMs(lightweight)
        return trees
            .filter { nowEpochMs - it.lastScanEpochMs >= minInterval }
            .sortedBy { it.lastScanEpochMs }
    }

    fun markScanned(
        trees: List<WatchedLibraryTree>,
        uri: String,
        nowEpochMs: Long,
    ): List<WatchedLibraryTree> =
        trees.map { tree ->
            if (tree.uri == uri) tree.copy(lastScanEpochMs = nowEpochMs) else tree
        }

    fun scanMadeLibraryChanges(inserted: Int, updated: Int, deleted: Int): Boolean =
        inserted > 0 || updated > 0 || deleted > 0

    internal fun canonicalUri(uri: String): String {
        val decoded = runCatching {
            java.net.URLDecoder.decode(uri, Charsets.UTF_8.name())
        }.getOrDefault(uri)
        return decoded.trimEnd('/')
    }

    private fun uriCovers(parentUri: String, childUri: String): Boolean {
        if (parentUri == childUri) return true
        val prefix = parentUri.trimEnd('/')
        return childUri.startsWith("$prefix/")
    }
}

internal fun parseWatchedLibraryTrees(value: String?): List<WatchedLibraryTree> {
    if (value.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(value)
        buildList(array.length()) {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val uri = obj.optString("uri").trim()
                val documentId = obj.optString("documentId").trim()
                if (uri.isBlank() || documentId.isBlank()) continue
                add(
                    WatchedLibraryTree(
                        uri = uri,
                        documentId = LibraryFolderWatchPolicy.normalizeDocumentId(documentId),
                        lastScanEpochMs = obj.optLong("lastScanEpochMs", 0L).coerceAtLeast(0L),
                        minDurationMs = obj.optLong("minDurationMs", LibraryScanOptions().minDurationMs)
                            .coerceAtLeast(0L),
                        minSizeBytes = obj.optLong("minSizeBytes", LibraryScanOptions().minSizeBytes)
                            .coerceAtLeast(0L),
                        excludeNonMusicFolders = obj.optBoolean("excludeNonMusicFolders", true),
                        excludeHiddenFolders = obj.optBoolean("excludeHiddenFolders", true),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}

internal fun formatWatchedLibraryTrees(trees: List<WatchedLibraryTree>): String {
    val array = JSONArray()
    trees.forEach { tree ->
        array.put(
            JSONObject().apply {
                put("uri", tree.uri)
                put("documentId", tree.documentId)
                put("lastScanEpochMs", tree.lastScanEpochMs)
                put("minDurationMs", tree.minDurationMs)
                put("minSizeBytes", tree.minSizeBytes)
                put("excludeNonMusicFolders", tree.excludeNonMusicFolders)
                put("excludeHiddenFolders", tree.excludeHiddenFolders)
            },
        )
    }
    return array.toString()
}
