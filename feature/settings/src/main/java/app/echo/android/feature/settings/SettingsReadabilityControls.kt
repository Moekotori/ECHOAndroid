package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import kotlin.math.roundToInt

/** Appearance and accessibility edit the same saved sizes and use the same draft previews. */
@Composable
internal fun SettingsReadabilityControls(
    importedFontFamily: FontFamily?,
    uiFontFamily: String,
    uiFontScale: Float,
    lyricsFontFamily: String,
    lyricsFontScale: Float,
    onUiFontScaleChange: (Float) -> Unit,
    onLyricsFontScaleChange: (Float) -> Unit,
) {
    SettingsSliderRow(
        title = stringResource(R.string.settings_ui_font_size),
        valueLabel = { "${(it * 100f).roundToInt()}%" },
        value = uiFontScale,
        valueRange = 0.88f..1.18f,
        steps = 14,
        onValueChange = onUiFontScaleChange,
        preview = { SettingsFontPreview(uiFontFamily, importedFontFamily, it, lyrics = false) },
    )
    SettingsSliderRow(
        title = stringResource(R.string.settings_lyrics_font_size),
        valueLabel = { "${(it * 100f).roundToInt()}%" },
        value = lyricsFontScale,
        valueRange = 0.82f..1.28f,
        steps = 22,
        onValueChange = onLyricsFontScaleChange,
        preview = { SettingsFontPreview(lyricsFontFamily, importedFontFamily, it, lyrics = true) },
    )
}
