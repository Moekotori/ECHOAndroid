package app.echo.android.data

import app.echo.android.model.library.CueSheet
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray

/** Successful expansions only. Persisted identities/fingerprints never contain audio or artwork. */
class CueScanCache(private val file: File? = null, private val capacity: Int = 8192) {
    private data class Entry(val stamp: String, val outputs: Map<String, String>)
    private val entries = LinkedHashMap<String, Entry>(16, .75f, true)
    private var loaded = false
    private var dirty = false

    fun reuse(source: TrackFingerprint, cue: CueSheet?, existing: Map<String, TrackFingerprint>, uncertain: Boolean = false): List<String>? {
        if (uncertain) return null
        load()
        val entry = entries[source.id] ?: return null
        if (stamp(source, cue) != entry.stamp || entry.outputs.any { (id, fingerprint) ->
                existing[id]?.fingerprint != fingerprint || fingerprint == LibraryScanPolicy.PendingDocumentMetadataFingerprint
            }) return null
        return entry.outputs.keys.toList()
    }

    fun remember(source: TrackFingerprint, cue: CueSheet?, outputs: List<LibraryTrackEntity>) {
        load()
        if (capacity <= 0 || outputs.isEmpty() || outputs.size > 4096) return
        val stamp = stamp(source, cue) ?: return
        val fingerprints = LinkedHashMap<String, String>()
        for (track in outputs) {
            val fingerprint = track.withFingerprint().fingerprint ?: return
            if (fingerprint == LibraryScanPolicy.PendingDocumentMetadataFingerprint) return
            fingerprints[track.id] = fingerprint
        }
        val next = Entry(stamp, fingerprints)
        if (entries[source.id] == next) return
        entries[source.id] = next
        while (entries.size > capacity) entries.remove(entries.keys.first())
        dirty = true
    }

    fun flush() {
        val target = file ?: return
        if (!dirty) return
        runCatching {
            val rows = JSONArray()
            var bytes = 0
            for ((id, entry) in entries.entries.toList().asReversed()) {
                val outputs = JSONArray()
                entry.outputs.forEach { (key, value) -> outputs.put(JSONArray().put(key).put(value)) }
                val row = JSONArray().put(id).put(entry.stamp).put(outputs)
                bytes += row.toString().toByteArray(Charsets.UTF_8).size + 1
                if (bytes > MaxBytes - 2) break
                rows.put(row)
            }
            target.parentFile?.mkdirs()
            val temp = File(target.path + ".tmp")
            temp.writeText(rows.toString())
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            dirty = false
            val older = target.parentFile?.listFiles().orEmpty().filter { it.name.startsWith("cue-scan-v1-") && it != target && it.extension == "json" }
                .sortedByDescending(File::lastModified)
            older.drop(15).forEach(File::delete)
        }
    }

    private fun load() {
        if (loaded) return
        loaded = true
        val source = file ?: return
        if (!source.isFile || source.length() > MaxBytes) return
        runCatching {
            val rows = JSONArray(source.readText())
            // Disk order is newest first; restore LRU insertion order from oldest to newest.
            for (i in minOf(rows.length(), capacity) - 1 downTo 0) {
                val row = rows.getJSONArray(i)
                val id = row.getString(0)
                val outputs = row.getJSONArray(2)
                if (id.length > 4096 || outputs.length() !in 1..4096) continue
                val fingerprints = LinkedHashMap<String, String>()
                for (j in 0 until outputs.length()) {
                    val pair = outputs.getJSONArray(j)
                    val key = pair.getString(0); val value = pair.getString(1)
                    if (key.length > 4096 || value.length > 1024) error("Invalid CUE cache entry")
                    fingerprints[key] = value
                }
                entries[id] = Entry(row.getString(1), fingerprints)
            }
        }.onFailure { entries.clear() }
    }

    private fun stamp(source: TrackFingerprint, cue: CueSheet?): String? {
        if (source.sizeBytes <= 0 || source.dateModifiedSeconds <= 0 || source.contentUri.isBlank()) return null
        val text = listOf(source.contentUri, source.sizeBytes.toString(), source.dateModifiedSeconds.toString(),
            source.relativePath.orEmpty(), source.fileName.orEmpty(), cue?.toString().orEmpty())
            .joinToString("") { "${it.length}:$it" }
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        val hex = "0123456789abcdef"
        return CharArray(digest.size * 2).also { out ->
            digest.forEachIndexed { index, byte ->
                val value = byte.toInt() and 255
                out[index * 2] = hex[value ushr 4]
                out[index * 2 + 1] = hex[value and 15]
            }
        }.concatToString()
    }

    companion object {
        private const val MaxBytes = 4 * 1024 * 1024
        fun scoped(directory: File, scope: String): CueScanCache = CueScanCache(File(directory,
            "cue-scan-v1-${UUID.nameUUIDFromBytes(scope.toByteArray(Charsets.UTF_8))}.json"))
    }
}

internal fun cueSourceFacts(id: String, uri: String, size: Long, modified: Long, path: String?, name: String?): TrackFingerprint =
    TrackFingerprint(id, uri, null, null, size, modified, path, fileName = name)

internal fun LibraryTrackEntity.cueSourceFacts(): TrackFingerprint =
    cueSourceFacts(id, contentUri, sizeBytes, dateModifiedSeconds, relativePath, fileName)
