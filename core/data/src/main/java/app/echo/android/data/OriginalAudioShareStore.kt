package app.echo.android.data

import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

internal data class OriginalAudioShareEntry(val source: String, val name: String, val mime: String, val size: Long)

/** Immutable manifests persist grants across process recreation. Only two decoded sessions are kept. */
internal class OriginalAudioShareStore(private val root: File, private val now: () -> Long = System::currentTimeMillis) {
    private data class Cached(val modified: Long, val length: Long, val created: Long, val entries: List<OriginalAudioShareEntry>)
    private val cached = object : LinkedHashMap<String, Cached>(2, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?): Boolean = size > 2
    }

    @Synchronized
    fun entry(session: String, index: Int, name: String): OriginalAudioShareEntry {
        if (!SessionId.matches(session) || index !in 0 until MaxFiles) throw FileNotFoundException()
        val directory = File(root, session)
        val manifest = File(directory, "manifest.json")
        val length = manifest.length()
        if (!manifest.isFile || length !in 1..MaxManifestBytes.toLong()) {
            cached.remove(session)
            throw FileNotFoundException()
        }
        val modified = manifest.lastModified()
        val data = cached[session]?.takeIf { it.modified == modified && it.length == length } ?: try {
            val document = JSONObject(manifest.readText(Charsets.UTF_8))
            val files = document.getJSONArray("files")
            if (files.length() !in 1..MaxFiles) throw FileNotFoundException()
            Cached(modified, length, document.optLong("createdAt", directory.lastModified()), List(files.length()) { at ->
                val file = files.getJSONObject(at)
                val source = file.getString("source")
                if (source.substringBefore(':') !in setOf("content", "file")) throw FileNotFoundException()
                OriginalAudioShareEntry(source, file.getString("name"), file.getString("mime"), file.optLong("size", -1))
            }).also { cached[session] = it }
        } catch (_: Exception) { throw FileNotFoundException() }
        if (now() - data.created > RetentionMs) {
            cached.remove(session)
            throw FileNotFoundException()
        }
        return data.entries.getOrNull(index)?.takeIf { it.name == name } ?: throw FileNotFoundException()
    }

    companion object {
        const val MaxTracks = 200
        const val MaxFiles = MaxTracks * 4
        const val RetentionMs = 24L * 60 * 60 * 1000
        const val MaxManifestBytes = 512 * 1024
        private val SessionId = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
