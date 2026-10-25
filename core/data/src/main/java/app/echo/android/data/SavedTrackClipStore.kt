package app.echo.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.echo.android.model.library.EchoSavedTrackClip
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.catch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.savedTrackClips by preferencesDataStore(name = "echo-saved-track-clips")

/** Independent atomic edits; at most 500 clip records / 128 KiB, never media or credentials. */
class SavedTrackClipStore(context: Context) {
    private val data = context.applicationContext.savedTrackClips
    private val key = stringPreferencesKey("clips")
    fun observe(trackId: String) = data.data.map { SavedTrackClipCodec.decode(it[key]).filter { clip -> trackId.isBlank() || clip.trackId == trackId }
        .sortedWith(compareBy({ it.startMs }, { it.endMs }, { it.id })) }.catch { error ->
        app.echo.android.model.error.EchoErrorLog.record(app.echo.android.model.error.EchoErrorSource.Library, "Saved clips could not be read", throwable = error)
        emit(emptyList())
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    suspend fun save(trackId: String, label: String, startMs: Long, endMs: Long, durationMs: Long, trackTitle: String = "") = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        require(trackId.isNotBlank() && trackId.length <= 2048 && label.trim().length in 1..100)
        val value = EchoSavedTrackClip(UUID.randomUUID().toString(), trackId, label.trim(), startMs, endMs, trackTitle.take(256))
        require(value.isPlayable(durationMs))
        data.edit { preferences ->
            val records = SavedTrackClipCodec.decode(preferences[key]).toMutableList()
            val index = records.indexOfFirst { it.trackId == trackId && it.startMs == startMs && it.endMs == endMs }
            if (index >= 0) records[index] = value.copy(id = records[index].id) else records.add(value)
            preferences[key] = SavedTrackClipCodec.encode(records)
        }

        Unit
    }
    suspend fun rename(id: String, label: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        require(label.trim().length in 1..100)
        data.edit { p -> p[key] = SavedTrackClipCodec.encode(SavedTrackClipCodec.decode(p[key]).map {
            if (it.id == id) it.copy(label = label.trim()) else it }) }

        Unit
    }
    suspend fun delete(id: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        data.edit { p -> p[key] = SavedTrackClipCodec.encode(SavedTrackClipCodec.decode(p[key]).filterNot { it.id == id }) }

        Unit
    }
}

internal object SavedTrackClipCodec {
    const val MaxRecords = 500
    const val MaxChars = 128 * 1024
    fun decode(raw: String?): List<EchoSavedTrackClip> {
        if (raw == null) return emptyList()
        require(raw.length <= MaxChars && raw.toByteArray(Charsets.UTF_8).size <= MaxChars)
        val array = JSONArray(raw)
        require(array.length() <= MaxRecords)
        return (0 until array.length()).map { i -> array.getJSONObject(i).let { value ->
            EchoSavedTrackClip(value.getString("id"), value.getString("trackId"), value.getString("label"),
                value.getLong("startMs"), value.getLong("endMs"), value.optString("trackTitle", "").take(256))
        } }.also { records ->
            require(records.map { it.id }.distinct().size == records.size)
            require(records.all { it.id.length in 1..100 && it.trackId.length in 1..2048 &&
                it.label.trim().length in 1..100 && it.isPlayable(Long.MAX_VALUE) })
        }
    }
    fun encode(records: List<EchoSavedTrackClip>): String {
        require(records.size <= MaxRecords)
        val array = JSONArray()
        records.forEach { clip -> array.put(JSONObject().put("id", clip.id).put("trackId", clip.trackId)
            .put("label", clip.label).put("trackTitle", clip.trackTitle.take(256)).put("startMs", clip.startMs).put("endMs", clip.endMs)) }
        return array.toString().also { require(it.length <= MaxChars && it.toByteArray(Charsets.UTF_8).size <= MaxChars) }
    }
}
