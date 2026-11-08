package app.echo.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.io.IOException

private val Context.searchHistoryData by preferencesDataStore(
    name = "echo-search-history",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** Phone-local search terms, newest first. Writes are serialized by DataStore. */
class EchoSearchHistoryStore internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.searchHistoryData)

    val queries = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { decode(it[Queries]) }.distinctUntilChanged()

    suspend fun record(query: String) {
        val term = query.trim().takeIf { it.isNotEmpty() && it.length <= MaxQueryLength } ?: return
        store.edit { values ->
            val recent = decode(values[Queries]).filterNot { it.equals(term, ignoreCase = true) }
            values[Queries] = JSONArray((listOf(term) + recent).take(MaxEntries)).toString()
        }
    }

    suspend fun remove(query: String) {
        store.edit { values ->
            values[Queries] = JSONArray(decode(values[Queries])
                .filterNot { it.equals(query.trim(), ignoreCase = true) }).toString()
        }
    }

    suspend fun clear() {
        store.edit { it.remove(Queries) }
    }

    private fun decode(raw: String?): List<String> {
        if (raw == null) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until minOf(array.length(), MaxEntries)) {
                    val term = (array.opt(index) as? String)?.trim() ?: continue
                    if (term.isNotEmpty() && term.length <= MaxQueryLength && none { it.equals(term, ignoreCase = true) }) add(term)
                }
            }
        } catch (_: org.json.JSONException) {
            emptyList()
        }
    }

    private companion object {
        val Queries = stringPreferencesKey("queries")
        const val MaxEntries = 20
        const val MaxQueryLength = 200
    }
}
