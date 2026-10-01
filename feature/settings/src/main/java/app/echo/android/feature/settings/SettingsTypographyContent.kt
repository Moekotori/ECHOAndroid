package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import kotlin.math.roundToInt

@Composable
internal fun SettingsTypographyContent(
    importedFontFamily: FontFamily?,
    uiFontFamily: String,
    uiFontScale: Float,
    lyricsFontFamily: String,
    lyricsFontScale: Float,
    importedFontUri: String?,
    onUiFontFamilyChange: (String) -> Unit,
    onUiFontScaleChange: (Float) -> Unit,
    onLyricsFontFamilyChange: (String) -> Unit,
    onLyricsFontScaleChange: (Float) -> Unit,
    onImportUiFont: () -> Unit,
    onImportLyricsFont: () -> Unit,
    onClearImportedFont: () -> Unit,
) {
    SettingsSectionCard(title = stringResource(R.string.settings_section_readability)) {
        SettingsSliderRow(
            title = stringResource(R.string.settings_ui_font_size),
            valueLabel = { "${(it * 100f).roundToInt()}%" },
            value = uiFontScale,
            valueRange = 0.88f..1.18f,
            steps = 14,
            onValueChange = onUiFontScaleChange,
            preview = { scale ->
                SettingsFontPreview(uiFontFamily, importedFontFamily, scale, lyrics = false)
            },
        )
        SettingsSliderRow(
            title = stringResource(R.string.settings_lyrics_font_size),
            valueLabel = { "${(it * 100f).roundToInt()}%" },
            value = lyricsFontScale,
            valueRange = 0.82f..1.28f,
            steps = 22,
            onValueChange = onLyricsFontScaleChange,
            preview = { scale ->
                SettingsFontPreview(lyricsFontFamily, importedFontFamily, scale, lyrics = true)
            },
        )
    }
    SettingsSectionCard(title = stringResource(R.string.settings_font_advanced), secondary = true) {
        SettingsChoiceGroupRow(
            title = stringResource(R.string.settings_ui_font),
            detail = fontDetail(uiFontFamily, importedFontUri),
            options = fontOptions(importedFontUri),
            selectedValue = uiFontFamily,
            onOptionSelected = { value ->
                if (value == "imported" && importedFontUri.isNullOrBlank()) {
                    onImportUiFont()
                } else {
                    onUiFontFamilyChange(value)
                }
            },
        )
        SettingsChoiceGroupRow(
            title = stringResource(R.string.settings_lyrics_font),
            detail = fontDetail(lyricsFontFamily, importedFontUri),
            options = fontOptions(importedFontUri),
            selectedValue = lyricsFontFamily,
            onOptionSelected = { value ->
                if (value == "imported" && importedFontUri.isNullOrBlank()) {
                    onImportLyricsFont()
                } else {
                    onLyricsFontFamilyChange(value)
                }
            },
        )
        SettingsActionRow(
            title = stringResource(if (importedFontUri.isNullOrBlank()) R.string.settings_import_font else R.string.settings_reselect_font),
            detail = if (importedFontUri.isNullOrBlank()) {
                stringResource(R.string.settings_import_font_detail)
            } else {
                stringResource(
                    R.string.settings_import_font_current,
                    importedFontUri.substringAfterLast('/').takeLast(28),
                )
            },
            onClick = onImportUiFont,
        )
        SettingsActionRow(
            title = stringResource(R.string.settings_clear_font),
            detail = if (importedFontUri.isNullOrBlank()) {
                stringResource(R.string.settings_clear_font_empty)
            } else {
                stringResource(R.string.settings_clear_font_detail)
            },
            enabled = !importedFontUri.isNullOrBlank(),
            actionLabel = stringResource(R.string.settings_clear_action),
            disabledLabel = stringResource(R.string.settings_unavailable),
            onClick = onClearImportedFont,
        )
    }
}
