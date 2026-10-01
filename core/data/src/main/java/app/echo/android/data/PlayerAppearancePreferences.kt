package app.echo.android.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.echo.android.model.settings.EchoLyricsPageStyle

/** Player-only presentation preferences; no playback-engine state lives here. */
internal object PlayerAppearancePreferences {
    /** The artwork page follows the app palette; saved styles keep their own presentation. */
    const val DefaultStyle = "classic"

    /** Light page used when 素笺阅读 is chosen from the dark song style. */
    const val LightStyle = "record_sleeve"

    private val Style = stringPreferencesKey("player_page_style")
    private val TextScale = floatPreferencesKey("player_text_scale")
    private val ArtworkScale = floatPreferencesKey("player_artwork_scale")

    fun style(preferences: Preferences): String = normalizeStyle(preferences[Style])
    fun textScale(preferences: Preferences): Float = normalizeScale(preferences[TextScale], 0.8f, 1.2f)
    fun artworkScale(preferences: Preferences): Float = normalizeScale(preferences[ArtworkScale], 0.7f, 1f)

    private val LyricsStyle = stringPreferencesKey("lyrics_page_style")
    private val ThemeMode = stringPreferencesKey("theme_mode")

    /** Only missing choices follow the theme. Explicit covers retain their established lyric binding. */
    fun lyricsStyle(preferences: Preferences, themeMode: String): EchoLyricsPageStyle {
        preferences[LyricsStyle]?.let { return EchoLyricsPageStyle.fromId(it) }
        if (preferences[Style] != null) return boundLyricsStyle(style(preferences))
        return if (themeMode == EchoThemeMode.Dark) EchoLyricsPageStyle.Mist else EchoLyricsPageStyle.Paper
    }

    /** Afterglow is independent; existing dark/light cover-to-lyric binding is otherwise unchanged. */
    fun boundLyricsStyle(playerStyle: String, current: EchoLyricsPageStyle = EchoLyricsPageStyle.Mist): EchoLyricsPageStyle =
        if (current.isAfterglow) current
        else if (normalizeStyle(playerStyle) == DefaultStyle) EchoLyricsPageStyle.Mist else EchoLyricsPageStyle.Paper

    /**
     * Keep the current light page when lyrics are already light.
     * Leaving 沉浸风格 for a light lyric page lands on [LightStyle].
     */
    fun boundPlayerStyle(lyrics: EchoLyricsPageStyle, playerStyle: String): String {
        val player = normalizeStyle(playerStyle)
        if (lyrics.isAfterglow) return player
        val darkPlayer = player == DefaultStyle
        val darkLyrics = lyrics == EchoLyricsPageStyle.Mist
        return when {
            darkLyrics && !darkPlayer -> DefaultStyle
            !darkLyrics && darkPlayer -> LightStyle
            else -> player
        }
    }

    fun write(preferences: MutablePreferences, style: String, textScale: Float, artworkScale: Float) {
        val next = normalizeStyle(style)
        if (next == this.style(preferences)) {
            // A size-only edit must not change the implicit lyric default when the cover is saved.
            preferences[LyricsStyle] = lyricsStyle(preferences, normalizeThemeMode(preferences[ThemeMode])).id
        }
        preferences[Style] = next
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
