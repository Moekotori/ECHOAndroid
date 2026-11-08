package app.echo.android.data

import androidx.datastore.preferences.core.*
import app.echo.android.model.settings.EchoAppearancePack
import org.json.JSONObject

/** Exact allowlist used for atomic apply and one-level undo, including absent preferences. */
internal object AppearancePackPreferences {
    val undoKey = stringPreferencesKey("appearance_pack_undo_v1")
    private val strings = listOf("player_page_style", "lyrics_page_style", "lyrics_backdrop", "lyrics_font_family", "lyrics_alignment", "lyrics_color_mode",
        "lyrics_word_highlight_style", "color_theme", "applied_saved_color_theme_id", "custom_background_mode", "custom_background_uri", "imported_font_uri")
    private val floats = listOf("player_text_scale", "player_artwork_scale", "lyrics_font_scale", "lyrics_line_spacing", "lyrics_background_dim",
        "lyrics_word_highlight_intensity", "custom_background_blur", "custom_background_brightness", "custom_background_glass",
        "lyrics_translation_scale", "lyrics_romanization_scale", "custom_background_scale", "custom_background_position_x", "custom_background_position_y")
    private val booleans = listOf("lyrics_show_translation", "lyrics_show_romanization", "dynamic_color_enabled", "lyrics_word_highlight_enabled", "lyrics_estimated_word_highlight_enabled")
    private val ints = listOf("custom_accent", "custom_secondary", "custom_background")

    fun capture(p: Preferences): String = JSONObject().apply {
        strings.forEach { put(it, p[stringPreferencesKey(it)] ?: JSONObject.NULL) }
        floats.forEach { put(it, p[floatPreferencesKey(it)] ?: JSONObject.NULL) }
        booleans.forEach { put(it, p[booleanPreferencesKey(it)] ?: JSONObject.NULL) }
        ints.forEach { put(it, p[intPreferencesKey(it)] ?: JSONObject.NULL) }
    }.toString()

    fun restore(p: MutablePreferences): Boolean {
        val raw = p[undoKey] ?: return false
        val j = JSONObject(raw)
        strings.forEach { val key = stringPreferencesKey(it); if (j.isNull(it)) p.remove(key) else p[key] = j.getString(it) }
        floats.forEach { val key = floatPreferencesKey(it); if (j.isNull(it)) p.remove(key) else p[key] = j.getDouble(it).toFloat() }
        booleans.forEach { val key = booleanPreferencesKey(it); if (j.isNull(it)) p.remove(key) else p[key] = j.getBoolean(it) }
        ints.forEach { val key = intPreferencesKey(it); if (j.isNull(it)) p.remove(key) else p[key] = j.getInt(it) }
        p.remove(undoKey)
        return true
    }

    fun apply(p: MutablePreferences, s: EchoAppearancePack, background: String?, font: String?) {
        p[undoKey] = capture(p)
        PlayerAppearancePreferences.write(p, s.playerStyle, s.textScale, s.artworkScale)
        fun str(key: String, value: String) { p[stringPreferencesKey(key)] = value }
        fun flt(key: String, value: Float) { p[floatPreferencesKey(key)] = value }
        str("lyrics_backdrop", s.lyricsBackdrop); str("lyrics_font_family", s.fontFamily); str("lyrics_alignment", s.alignment)
        str("lyrics_word_highlight_style", s.wordStyle); str("color_theme", s.colorTheme)
        str("lyrics_color_mode", s.colorMode)
        flt("lyrics_font_scale", s.fontScale); flt("lyrics_line_spacing", s.lineSpacing); flt("lyrics_background_dim", s.backgroundDim)
        flt("lyrics_word_highlight_intensity", s.wordIntensity)
        flt("custom_background_blur", s.backgroundBlur); flt("custom_background_brightness", s.backgroundBrightness); flt("custom_background_glass", s.backgroundGlass)
        flt("lyrics_translation_scale", s.translationScale); flt("lyrics_romanization_scale", s.romanizationScale)
        flt("custom_background_scale", s.backgroundScale); flt("custom_background_position_x", s.backgroundPositionX); flt("custom_background_position_y", s.backgroundPositionY)
        p[booleanPreferencesKey("lyrics_word_highlight_enabled")] = s.wordHighlightEnabled
        p[booleanPreferencesKey("lyrics_estimated_word_highlight_enabled")] = s.estimatedWordHighlightEnabled
        p[booleanPreferencesKey("lyrics_show_translation")] = s.showTranslation
        p[booleanPreferencesKey("lyrics_show_romanization")] = s.showRomanization
        p[booleanPreferencesKey("dynamic_color_enabled")] = s.dynamicColorEnabled
        p[intPreferencesKey("custom_accent")] = s.colors.accent
        p[intPreferencesKey("custom_secondary")] = s.colors.secondary
        p[intPreferencesKey("custom_background")] = s.colors.background
        p.remove(stringPreferencesKey("applied_saved_color_theme_id"))
        str("custom_background_mode", if (background != null) "image" else "default")
        val backgroundKey = stringPreferencesKey("custom_background_uri")
        if (background == null) p.remove(backgroundKey) else p[backgroundKey] = background
        val fontKey = stringPreferencesKey("imported_font_uri")
        if (font == null) p.remove(fontKey) else p[fontKey] = font
    }
}
