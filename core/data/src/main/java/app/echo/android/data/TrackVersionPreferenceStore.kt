package app.echo.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

private val Context.trackVersionPreferences by preferencesDataStore(name = "echo-track-versions")

internal class TrackVersionPreferenceStore(context: Context) {
    private val store = context.applicationContext.trackVersionPreferences
    private fun key(group: String) = stringPreferencesKey(MessageDigest.getInstance("SHA-256")
        .digest(group.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) })

    suspend fun snapshot(): Map<String, String> = store.data.first().asMap()
        .mapValues { it.value.toString().substringAfter('|') }.mapKeys { it.key.name }

    fun preferred(snapshot: Map<String, String>, group: String): String? = snapshot[key(group).name]

    suspend fun save(group: String, id: String?) {
        store.edit { values ->
            if (id == null) values.remove(key(group))
            else values[key(group)] = "${System.currentTimeMillis()}|$id"
            values.asMap().entries.sortedByDescending { it.value.toString().substringBefore('|').toLongOrNull() ?: 0L }
                .drop(1024).forEach { values.remove(stringPreferencesKey(it.key.name)) }
        }
    }
}
