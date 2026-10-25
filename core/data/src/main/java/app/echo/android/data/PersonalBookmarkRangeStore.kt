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

    /** Stage copies before the Room transaction. Original keys survive failure or cancellation. */
    suspend fun prepareMerge(moves: List<BookmarkMergeMove>): Boolean {
        if (moves.isEmpty()) return true
        var complete = true
        store.edit { preferences ->
            val json = JSONObject(preferences[records] ?: "{}")
            for (move in moves) {
                val oldKey = key(move.original.trackId, move.original.positionMs)
                val record = json.optJSONArray(oldKey) ?: continue
                val nextKey = key(move.destination.trackId, move.destination.positionMs)
                if (json.has(nextKey)) continue
                val end = move.endPosition(record.getLong(0)) ?: continue
                if (end > EchoPersonalStateLimits.MaxDurationMs) { complete = false; break }
                json.put(nextKey, JSONArray(listOf(end, record.getLong(1))))
            }
            val encoded = json.toString()
            if (json.length() > EchoPersonalStateLimits.Bookmarks || encoded.length > 128 * 1024) complete = false
            if (complete) preferences[records] = encoded
        }
        return complete
    }

    suspend fun completeMerge(moves: List<BookmarkMergeMove>) {
        if (moves.isEmpty()) return
        store.edit { preferences ->
            val json = JSONObject(preferences[records] ?: "{}")
            moves.forEach { move ->
                val old = key(move.original.trackId, move.original.positionMs)
                if (old != key(move.destination.trackId, move.destination.positionMs)) json.remove(old)
            }
            preferences[records] = json.toString()
        }
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
