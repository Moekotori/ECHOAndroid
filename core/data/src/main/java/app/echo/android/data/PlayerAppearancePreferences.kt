package app.echo.android.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.echo.android.model.settings.EchoLyricsPageStyle

/** Player-only presentation preferences; no playback-engine state lives here. */
internal object PlayerAppearancePreferences {
    /** 沉浸风格. Missing or unknown values use this dark page, paired with 雾夜沉浸. */
    const val DefaultStyle = "classic"

    /** Light page used when 素笺阅读 is chosen from the dark song style. */
    const val LightStyle = "record_sleeve"

    private val Style = stringPreferencesKey("player_page_style")
    private val TextScale = floatPreferencesKey("player_text_scale")
    private val ArtworkScale = floatPreferencesKey("player_artwork_scale")

    fun style(preferences: Preferences): String = normalizeStyle(preferences[Style])
    fun textScale(preferences: Preferences): Float = normalizeScale(preferences[TextScale], 0.8f, 1.2f)
    fun artworkScale(preferences: Preferences): Float = normalizeScale(preferences[ArtworkScale], 0.7f, 1f)

    /** Dark song page uses the dark lyric base; every light song page uses the light lyric page. */
    fun boundLyricsStyle(playerStyle: String): EchoLyricsPageStyle =
        if (normalizeStyle(playerStyle) == DefaultStyle) EchoLyricsPageStyle.Mist else EchoLyricsPageStyle.Paper

    /**
     * Keep the current light page when lyrics are already light.
     * Leaving 沉浸风格 for a light lyric page lands on [LightStyle].
     */
    fun boundPlayerStyle(lyrics: EchoLyricsPageStyle, playerStyle: String): String {
        val player = normalizeStyle(playerStyle)
        val darkPlayer = player == DefaultStyle
        val darkLyrics = lyrics == EchoLyricsPageStyle.Mist
        return when {
            darkLyrics && !darkPlayer -> DefaultStyle
            !darkLyrics && darkPlayer -> LightStyle
            else -> player
        }
    }

    fun write(preferences: MutablePreferences, style: String, textScale: Float, artworkScale: Float) {
        preferences[Style] = normalizeStyle(style)
        preferences[TextScale] = normalizeScale(textScale, 0.8f, 1.2f)
        preferences[ArtworkScale] = normalizeScale(artworkScale, 0.7f, 1f)
    }

    private fun normalizeStyle(value: String?): String = when (value) {
        "classic", "record_sleeve", "pixel_handheld", "type_poster" -> value
        else -> DefaultStyle
    }
    private fun normalizeScale(value: Float?, min: Float, max: Float): Float =
        value?.takeIf { it.isFinite() }?.coerceIn(min, max) ?: 1f
}
