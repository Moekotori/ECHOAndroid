package app.echo.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.echo.android.model.connect.EchoPersonalStateLimits
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

private val Context.personalBookmarkRanges by preferencesDataStore(name = "echo-personal-bookmark-ranges")

/** Retain imported PC segment ends, while Android's existing saved points stay point bookmarks. */
internal class PersonalBookmarkRangeStore(context: Context) {
    private val store = context.applicationContext.personalBookmarkRanges
    private val records = stringPreferencesKey("ranges")
    fun key(trackId: String, positionMs: Long): String = MessageDigest.getInstance("SHA-256")
        .digest("$trackId\u0000$positionMs".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    suspend fun snapshot(): Map<String, Long> {
        val raw = store.data.first()[records] ?: return emptyMap()
        require(raw.length <= 128 * 1024)
        val json = JSONObject(raw)
        require(json.length() <= EchoPersonalStateLimits.Bookmarks)
        return json.keys().asSequence().associateWith { json.getJSONArray(it).getLong(0) }
    }

    suspend fun set(trackId: String, positionMs: Long, endPositionMs: Long?) {
        store.edit { preferences ->
            val json = JSONObject(preferences[records] ?: "{}")
            val id = key(trackId, positionMs)
            if (endPositionMs == null) json.remove(id)
            else {
                require(endPositionMs > positionMs && endPositionMs <= EchoPersonalStateLimits.MaxDurationMs)
                json.put(id, JSONArray(listOf(endPositionMs, System.currentTimeMillis())))
            }
            json.keys().asSequence().toList().sortedByDescending { json.getJSONArray(it).getLong(1) }
                .drop(EchoPersonalStateLimits.Bookmarks).forEach(json::remove)
            preferences[records] = json.toString()
        }
    }
}
