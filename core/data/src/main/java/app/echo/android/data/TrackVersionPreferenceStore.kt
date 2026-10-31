package app.echo.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackVersionPolicy
import app.echo.android.model.library.EchoDuplicateVersionPolicy

private val Context.trackVersionPreferences by preferencesDataStore(name = "echo-track-versions")

internal class TrackVersionPreferenceStore(context: Context) {
    private val store = context.applicationContext.trackVersionPreferences
    private fun key(group: String) = stringPreferencesKey(MessageDigest.getInstance("SHA-256")
        .digest(group.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) })

    suspend fun snapshot(): Map<String, String> = store.data.first().asMap()
        .mapValues { it.value.toString().substringAfter('|') }.mapKeys { it.key.name }

    fun preferred(snapshot: Map<String, String>, group: String): String? = snapshot[key(group).name]
    fun token(group: String): String = key(group).name
    fun group(track: EchoTrack, albumIdentity: String?): String? = EchoTrackVersionPolicy.key(track)?.let {
        if (track.source.isLocalAudioFile && !albumIdentity.isNullOrBlank()) "${EchoDuplicateVersionPolicy.associationKey(track)}\u0000album:$albumIdentity" else it
    }
    fun previousGroup(track: EchoTrack, albumIdentity: String?): String? = EchoTrackVersionPolicy.key(track)?.let {
        if (track.source.isLocalAudioFile && !albumIdentity.isNullOrBlank()) "$it\u0000album:$albumIdentity" else it
    }
    fun choice(snapshot: Map<String, String>, group: String, legacy: String, previous: String? = null): String? =
        listOfNotNull(group, previous, legacy).firstOrNull { snapshot.containsKey(token(it)) }
            ?.let { snapshot[token(it)]?.takeIf(String::isNotEmpty) }

    suspend fun save(group: String, id: String?, keepAutomatic: Boolean = false) {
        store.edit { values ->
            if (id == null && !keepAutomatic) values.remove(key(group))
            else values[key(group)] = "${System.currentTimeMillis()}|${id.orEmpty()}"
            values.asMap().entries.sortedByDescending { it.value.toString().substringBefore('|').toLongOrNull() ?: 0L }
                .drop(1024).forEach { values.remove(stringPreferencesKey(it.key.name)) }
        }
    }
}
