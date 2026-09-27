package app.echo.android.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** Player-only presentation preferences; no playback-engine state lives here. */
internal object PlayerAppearancePreferences {
    private val Style = stringPreferencesKey("player_page_style")
    private val TextScale = floatPreferencesKey("player_text_scale")
    private val ArtworkScale = floatPreferencesKey("player_artwork_scale")

    fun style(preferences: Preferences): String = normalizeStyle(preferences[Style])
    fun textScale(preferences: Preferences): Float = normalizeScale(preferences[TextScale], 0.8f, 1.2f)
    fun artworkScale(preferences: Preferences): Float = normalizeScale(preferences[ArtworkScale], 0.7f, 1f)

    fun write(preferences: MutablePreferences, style: String, textScale: Float, artworkScale: Float) {
        preferences[Style] = normalizeStyle(style)
        preferences[TextScale] = normalizeScale(textScale, 0.8f, 1.2f)
        preferences[ArtworkScale] = normalizeScale(artworkScale, 0.7f, 1f)
    }

    private fun normalizeStyle(value: String?): String = if (value == "classic") "classic" else "record_sleeve"
    private fun normalizeScale(value: Float?, min: Float, max: Float): Float =
        value?.takeIf { it.isFinite() }?.coerceIn(min, max) ?: 1f
}
