package app.echo.android.data

import app.echo.android.model.library.LibraryScanOptions
import org.json.JSONArray
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Worker-thread cache of successfully read files rejected by duration/size, never failed reads. */
class LocalScanFilterCache(
    private val file: File? = null,
    private val capacity: Int = 10_000,
) {
    private data class Entry(val uri: String, val size: Long, val modified: Long, val duration: Long)
    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private var loaded = false
    private var dirty = false

    fun shouldSkip(uri: String, size: Long, modified: Long, options: LibraryScanOptions): Boolean {
        load()
        val entry = entries[uri] ?: return false
        if (size <= 0L || modified <= 0L || entry.size != size || entry.modified != modified ||
            options.accepts(entry.duration, size, null)) {
            entries.remove(uri)
            dirty = true
            return false
        }
        return true
    }

    fun remember(uri: String, size: Long, modified: Long, duration: Long) {
        load()
        if (capacity <= 0 || size <= 0L || modified <= 0L || uri.length > 2048) return
        val entry = Entry(uri, size, modified, duration)
        if (entries[uri] == entry) return
        entries[uri] = entry
        while (entries.size > capacity) entries.remove(entries.keys.first())
        dirty = true
    }

    fun hasEntries(): Boolean { load(); return entries.isNotEmpty() }

    /** At most one bounded, atomic cache write per scan. Failure only causes a later cache miss. */
    fun flush() {
        val target = file ?: return
        if (!dirty) return
        runCatching {
            var bytes = 0
            val recent = entries.values.toList().asReversed().takeWhile {
                bytes += it.uri.toByteArray(Charsets.UTF_8).size + 100
                bytes < MaxFileBytes
            }.asReversed()
            val data = JSONArray()
            recent.forEach { data.put(JSONArray().put(it.uri).put(it.size).put(it.modified).put(it.duration)) }
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
                val entry = Entry(row.getString(0), row.getLong(1), row.getLong(2), row.getLong(3))
                if (entry.uri.length <= 2048 && entry.size > 0L && entry.modified > 0L) entries[entry.uri] = entry
            }
        }.onFailure { entries.clear() }
    }

    private companion object { const val MaxFileBytes = 4 * 1024 * 1024 }
}

/** A coarse candidate filter only; the scanner still verifies the real filename before merging. */
internal fun documentDuplicateCandidates(
    native: Sequence<TrackFingerprint>,
    documents: List<TrackFingerprint>,
): List<TrackFingerprint> {
    val snapshots = documents.asSequence().filter { it.sizeBytes > 0L && it.dateModifiedSeconds > 0L }
        .map { Triple(it.relativePath, it.sizeBytes, it.dateModifiedSeconds) }.toHashSet()
    if (snapshots.isEmpty()) return emptyList()
    return native.filter { LibraryScanPolicy.isMediaStoreNativeId(it.id) &&
        Triple(it.relativePath, it.sizeBytes, it.dateModifiedSeconds) in snapshots }.toList()
}
