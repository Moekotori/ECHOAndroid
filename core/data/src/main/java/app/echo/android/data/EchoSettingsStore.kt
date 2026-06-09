package app.echo.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.echoSettings by preferencesDataStore(name = "echo-settings")

data class EchoPlaybackSettings(
    val preferOffload: Boolean = true,
    val lastOutputRoute: String = "system",
    val showLyricsControlDeck: Boolean = false,
)

class EchoSettingsStore(
    private val context: Context,
) {
    val playbackSettings: Flow<EchoPlaybackSettings> =
        context.echoSettings.data.map { preferences ->
            EchoPlaybackSettings(
                preferOffload = preferences[Keys.PreferOffload] ?: true,
                lastOutputRoute = preferences[Keys.LastOutputRoute] ?: "system",
                showLyricsControlDeck = preferences[Keys.ShowLyricsControlDeck] ?: false,
            )
        }

    suspend fun setPreferOffload(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.PreferOffload] = enabled }
    }

    suspend fun setLastOutputRoute(route: String) {
        context.echoSettings.edit { it[Keys.LastOutputRoute] = route }
    }

    suspend fun setShowLyricsControlDeck(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.ShowLyricsControlDeck] = enabled }
    }

    private object Keys {
        val PreferOffload = booleanPreferencesKey("prefer_offload")
        val LastOutputRoute = stringPreferencesKey("last_output_route")
        val ShowLyricsControlDeck = booleanPreferencesKey("show_lyrics_control_deck")
    }
}
