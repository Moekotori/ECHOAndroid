package app.echo.android.lyrics

import android.content.Context
import android.net.Uri
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
                preferences[Keys.Bindings]
                    ?.let(::parseBindings)
                    ?.optString(trackId)
                    ?.takeIf(String::isNotBlank)
                    ?.let(Uri::parse)
            }
            .first()

    suspend fun bindLyrics(trackId: String, uri: Uri) {
        context.echoImportedLyrics.edit { preferences ->
            val bindings = preferences[Keys.Bindings]?.let(::parseBindings) ?: JSONObject()
            bindings.put(trackId, uri.toString())
            preferences[Keys.Bindings] = bindings.toString()
        }
    }

    suspend fun lyricsOffsetForTrack(trackId: String): Long =
        context.echoImportedLyrics.data
            .map { preferences ->
                preferences[Keys.Offsets]
                    ?.let(::parseBindings)
                    ?.optLong(trackId, 0L)
                    ?: 0L
            }
            .first()

    suspend fun setLyricsOffset(trackId: String, offsetMs: Long) {
        context.echoImportedLyrics.edit { preferences ->
            val offsets = preferences[Keys.Offsets]?.let(::parseBindings) ?: JSONObject()
            if (offsetMs == 0L) {
                offsets.remove(trackId)
            } else {
                offsets.put(trackId, offsetMs)
            }
            preferences[Keys.Offsets] = offsets.toString()
        }
    }

    private fun parseBindings(raw: String): JSONObject =
        runCatching { JSONObject(raw) }.getOrDefault(JSONObject())

    private object Keys {
        val Bindings = stringPreferencesKey("track_lyrics_uri_bindings")
        val Offsets = stringPreferencesKey("track_lyrics_offsets")
    }
}
