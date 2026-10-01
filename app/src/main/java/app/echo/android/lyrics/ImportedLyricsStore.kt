package app.echo.android.lyrics

import android.content.Context
import android.net.Uri
import app.echo.android.model.lyrics.EchoLyrics
import java.io.File
import java.security.MessageDigest
import android.util.AtomicFile
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.echoImportedLyrics by preferencesDataStore(name = "echo-imported-lyrics")

class ImportedLyricsStore(
    private val context: Context,
) {
    suspend fun lyricsUriForTrack(trackId: String): Uri? =
        context.echoImportedLyrics.data
            .map { preferences ->
                preferences[Keys.BINDINGS]
                    ?.let(::parseBindings)
                    ?.optString(trackId)
                    ?.takeIf(String::isNotBlank)
                    ?.let(Uri::parse)
            }
            .first()

    suspend fun bindLyrics(trackId: String, uri: Uri) {
        context.echoImportedLyrics.edit { preferences ->
            val bindings = preferences[Keys.BINDINGS]?.let(::parseBindings) ?: JSONObject()
            bindings.put(trackId, uri.toString())
            preferences[Keys.BINDINGS] = bindings.toString()
        }
    }

    suspend fun lyricsOffsetForTrack(trackId: String): Long =
        context.echoImportedLyrics.data
            .map { preferences ->
                preferences[Keys.OFFSETS]
                    ?.let(::parseBindings)
                    ?.optLong(trackId, 0L)
                    ?: 0L
            }
            .first()

    suspend fun setLyricsOffset(trackId: String, offsetMs: Long) {
        context.echoImportedLyrics.edit { preferences ->
            val offsets = preferences[Keys.OFFSETS]?.let(::parseBindings) ?: JSONObject()
            if (offsetMs == 0L) {
                offsets.remove(trackId)
            } else {
                offsets.put(trackId, offsetMs)
            }
            preferences[Keys.OFFSETS] = offsets.toString()
        }
    }

    suspend fun unbindLyrics(trackId: String) {
        context.echoImportedLyrics.edit { preferences ->
            val bindings = preferences[Keys.BINDINGS]?.let(::parseBindings) ?: JSONObject()
            bindings.remove(trackId)
            preferences[Keys.BINDINGS] = bindings.toString()
        }
        synchronized(this) { storedFile(trackId, selected = true).delete() }
    }

    @Synchronized
    fun readSaved(trackId: String, selected: Boolean): EchoLyrics? {
        val file = storedFile(trackId, selected)
        if (!file.isFile || file.length() > MAX_BYTES) return null
        return runCatching { EchoLyricsJson.decode(file.readText()) }.getOrNull()?.also {
            file.setLastModified(System.currentTimeMillis())
        }
    }

    @Synchronized
    fun save(trackId: String, lyrics: EchoLyrics, selected: Boolean) {
        val bytes = JSONObject(EchoLyricsJson.encode(lyrics)).put("echoTrackId", trackId).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Lyrics file is too large" }
        val file = storedFile(trackId, selected)
        file.parentFile?.mkdirs()
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try { output.write(bytes); atomic.finishWrite(output) }
        catch (error: Exception) { atomic.failWrite(output); throw error }
        // Explicit user selections are durable files. Only automatic downloads are evictable cache.
        if (!selected) {
            val files = file.parentFile?.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
            var bytesKept = 0L
            files.forEachIndexed { index, cached ->
                bytesKept += cached.length()
                if (index >= 128 || bytesKept > 24L * 1024 * 1024) cached.delete()
            }
        }
    }

    @Synchronized
    fun clearCached(trackId: String) { storedFile(trackId, selected = false).delete() }

    suspend fun selectedForBackup(lookupLegacyIds: suspend (Set<String>) -> Map<String,String>): Map<String,Pair<String?,Long>> {
        val prefs = context.echoImportedLyrics.data.first()
        val bindings = prefs[Keys.BINDINGS]?.let(::parseBindings) ?: JSONObject()
        val offsets = prefs[Keys.OFFSETS]?.let(::parseBindings) ?: JSONObject()
        val ids = (bindings.keys().asSequence().toList() + offsets.keys().asSequence().toList()).toMutableSet()
        val files = java.io.File(context.filesDir, "lyrics/selected").listFiles()?.filter { it.extension == "json" }.orEmpty()
        require(files.size <= 10000)
        val unknown = mutableMapOf<String,String>()
        val texts = mutableMapOf<String,String>()
        var bytes = 0L
        files.forEach { file ->
            require(file.length() <= MAX_BYTES)
            bytes += file.length(); require(bytes <= 16L * 1024 * 1024)
            val json = JSONObject(file.readText())
            val id = json.optString("echoTrackId")
            val sanitized = EchoLyricsJson.decode(json.toString()).let { lyrics ->
                lyrics.copy(metadata = lyrics.metadata.filterKeys { key -> !app.echo.android.data.EchoBackupCodec.isForbiddenKey(key) })
            }
            val text = EchoLyricsJson.encode(sanitized)
            if (id.isNotBlank()) { ids += id; texts[id] = text } else unknown[file.nameWithoutExtension] = text
        }
        val legacy = lookupLegacyIds(unknown.keys)
        unknown.forEach { (hash,text) -> legacy[hash]?.let { id -> ids += id; texts[id] = text } }
        require(ids.size <= 10000)
        return ids.associateWith { id -> texts[id] to offsets.optLong(id,0).coerceIn(-30000,30000) }
    }

    private fun storedFile(trackId: String, selected: Boolean): File {
        val key = MessageDigest.getInstance("SHA-256").digest(trackId.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "lyrics/${if (selected) "selected" else "downloads"}/$key.json")
    }

    private companion object { const val MAX_BYTES = 2 * 1024 * 1024 }

    private fun parseBindings(raw: String): JSONObject =
        runCatching { JSONObject(raw) }.getOrDefault(JSONObject())

    private object Keys {
        val BINDINGS = stringPreferencesKey("track_lyrics_uri_bindings")
        val OFFSETS = stringPreferencesKey("track_lyrics_offsets")
    }
}
