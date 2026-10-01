package app.echo.android.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.echo.android.model.settings.EchoLyricsPageStyle
import app.echo.android.model.settings.EchoPlayerPageStyle

/** Player-only presentation preferences; no playback-engine state lives here. */
internal object PlayerAppearancePreferences {
    /** The artwork page follows the app palette; saved styles keep their own presentation. */
    const val DefaultStyle = "classic"

    private val Style = stringPreferencesKey("player_page_style")
    private val TextScale = floatPreferencesKey("player_text_scale")
    private val ArtworkScale = floatPreferencesKey("player_artwork_scale")

    fun style(preferences: Preferences): String =
        EchoPlayerPageStyle.fromLegacy(preferences[Style], preferences[LyricsStyle]).id
    fun textScale(preferences: Preferences): Float = normalizeScale(preferences[TextScale], 0.8f, 1.2f)
    fun artworkScale(preferences: Preferences): Float = normalizeScale(preferences[ArtworkScale], 0.7f, 1f)

    private val LyricsStyle = stringPreferencesKey("lyrics_page_style")
    private val LyricsFontFamily = stringPreferencesKey("lyrics_font_family")
    private val LyricsAlignment = stringPreferencesKey("lyrics_alignment")
    private val LyricsColorMode = stringPreferencesKey("lyrics_color_mode")
    fun lyricsStyle(preferences: Preferences): EchoLyricsPageStyle =
        EchoPlayerPageStyle.fromId(style(preferences)).lyricsPreset

    fun write(preferences: MutablePreferences, style: String, textScale: Float, artworkScale: Float) {
        val next = EchoPlayerPageStyle.fromId(style)
        if (next.id != this.style(preferences)) {
            preferences[LyricsFontFamily] = next.defaultFontFamily
            preferences[LyricsAlignment] = next.lyricsPreset.defaultAlignment
            preferences[LyricsColorMode] = EchoLyricsColorMode.White
        }
        preferences[Style] = next.id
        // Keep legacy backup fields coherent while deriving both screens from one choice.
        preferences[LyricsStyle] = next.lyricsPreset.id
        preferences[TextScale] = normalizeScale(textScale, 0.8f, 1.2f)
        preferences[ArtworkScale] = normalizeScale(artworkScale, 0.7f, 1f)
    }

    fun restore(preferences: MutablePreferences, player: String?, lyrics: String?, textScale: Float?, artworkScale: Float?) {
        if (player == null && lyrics == null && textScale == null && artworkScale == null) return
        val next = if (player != null || lyrics != null) EchoPlayerPageStyle.fromLegacy(player, lyrics).id
            else style(preferences)
        write(preferences, next, textScale ?: this.textScale(preferences), artworkScale ?: this.artworkScale(preferences))
    }

    private fun normalizeScale(value: Float?, min: Float, max: Float): Float =
        value?.takeIf { it.isFinite() }?.coerceIn(min, max) ?: 1f
}
