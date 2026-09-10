package app.echo.android.feature.settings

import app.echo.android.design.backgroundMaxBlur
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import android.os.Build
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.model.settings.EchoBackgroundStyle
import kotlin.math.roundToInt

@Composable
internal fun SettingsAppearanceContent(
    dynamicColorEnabled: Boolean,
    customBackgroundMode: String,
    customBackgroundUri: String?,
    customBackgroundBlur: Float,
    customBackgroundBrightness: Float,
    customBackgroundGlass: Float,
    customBackgroundScale: Float,
    uiFontFamily: String,
    uiFontScale: Float,
    uiDensityScale: Float,
    lyricsFontFamily: String,
    lyricsFontScale: Float,
    importedFontUri: String?,
    themeMode: String,
    scheduledDarkModeEnabled: Boolean,
    scheduledDarkStartMinute: Int,
    scheduledDarkEndMinute: Int,
    onDynamicColorEnabledChange: (Boolean) -> Unit,
    onPickImageBackground: () -> Unit,
    onPickVideoBackground: () -> Unit,
    onClearCustomBackground: () -> Unit,
    onCustomBackgroundBlurChange: (Float) -> Unit,
    onCustomBackgroundBrightnessChange: (Float) -> Unit,
    onCustomBackgroundGlassChange: (Float) -> Unit,
    onCustomBackgroundScaleChange: (Float) -> Unit,
    onCustomBackgroundStyleChange: (EchoBackgroundStyle) -> Unit,
    onUiFontFamilyChange: (String) -> Unit,
    onUiFontScaleChange: (Float) -> Unit,
    onUiDensityScaleChange: (Float) -> Unit,
    onLyricsFontFamilyChange: (String) -> Unit,
    onLyricsFontScaleChange: (Float) -> Unit,
    onImportUiFont: () -> Unit,
    onImportLyricsFont: () -> Unit,
    onClearImportedFont: () -> Unit,
    onThemeModeChange: (String) -> Unit,
    onScheduledDarkModeEnabledChange: (Boolean) -> Unit,
    onScheduledDarkStartMinuteChange: (Int) -> Unit,
    onScheduledDarkEndMinuteChange: (Int) -> Unit,
) {
    var advancedTheme by rememberSaveable { mutableStateOf(scheduledDarkModeEnabled) }
    var customBackgroundAdvancedExpanded by rememberSaveable { mutableStateOf(false) }
    SettingsSectionCard(
        title = stringResource(R.string.settings_section_theme),
    ) {
        ThemeModeSelector(
            selectedMode = themeMode,
            onSelect = onThemeModeChange,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_dynamic_color),
            detail = if (Build.VERSION.SDK_INT >= 31) {
                stringResource(R.string.settings_dynamic_color_detail)
            } else {
                stringResource(R.string.settings_dynamic_color_unavailable)
            },
            checked = dynamicColorEnabled && Build.VERSION.SDK_INT >= 31,
            onCheckedChange = onDynamicColorEnabledChange,
            enabled = Build.VERSION.SDK_INT >= 31,
        )
        SettingsDisclosureRow(
            title = stringResource(R.string.settings_advanced),
            detail = stringResource(R.string.settings_scheduled_dark),
            expanded = advancedTheme,
            onExpandedChange = { advancedTheme = it },
        )
        if (advancedTheme) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_scheduled_dark),
                detail = stringResource(
                    R.string.settings_scheduled_dark_detail,
                    formatMinuteOfDay(scheduledDarkStartMinute),
                    formatMinuteOfDay(scheduledDarkEndMinute),
                ),
                checked = scheduledDarkModeEnabled,
                onCheckedChange = onScheduledDarkModeEnabledChange,
            )
            if (scheduledDarkModeEnabled) {
                SettingsSliderRow(
                    title = stringResource(R.string.settings_dark_start),
                    valueLabel = { formatMinuteOfDay(it.roundToInt()) },
                    value = scheduledDarkStartMinute.toFloat(),
                    valueRange = 0f..1439f,
                    steps = 95,
                    onValueChange = { onScheduledDarkStartMinuteChange(it.roundToQuarterHour()) },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.settings_dark_end),
                    valueLabel = { formatMinuteOfDay(it.roundToInt()) },
                    value = scheduledDarkEndMinute.toFloat(),
                    valueRange = 0f..1439f,
                    steps = 95,
                    onValueChange = { onScheduledDarkEndMinuteChange(it.roundToQuarterHour()) },
                )
            }
        }
    }

    SettingsSectionCard(
        title = stringResource(R.string.settings_section_background),
        persistentContent = {
            SettingsBackgroundSourceRow(
                mode = customBackgroundMode,
                uri = customBackgroundUri,
                onPickImageBackground = onPickImageBackground,
                onPickVideoBackground = onPickVideoBackground,
                onClearCustomBackground = onClearCustomBackground,
            )
        },
    ) {
        val backgroundDisabled = customBackgroundMode == "video" &&
            LocalEchoEffectivePerformanceMode.current.isLightweight
        val maxBlur = LocalEchoEffectivePerformanceMode.current.backgroundMaxBlur
        if (customBackgroundMode != "default" && !customBackgroundUri.isNullOrBlank() && !backgroundDisabled) {
            val selectedStyle = EchoBackgroundStyle.entries.firstOrNull {
                it.matches(customBackgroundBlur, customBackgroundBrightness, customBackgroundGlass,
                    customBackgroundScale, maxBlur, isVideo = customBackgroundMode == "video")
            }
            SettingsChoiceGroupRow(
                title = stringResource(R.string.settings_bg_style),
                detail = if (selectedStyle == null) stringResource(R.string.settings_bg_style_custom)
                    else backgroundStyleLabel(selectedStyle),
                options = EchoBackgroundStyle.entries.map { SettingsChoiceOption(it.id, backgroundStyleLabel(it)) },
                selectedValue = selectedStyle?.id.orEmpty(),
                onOptionSelected = { id ->
                    EchoBackgroundStyle.entries.firstOrNull { it.id == id }?.let(onCustomBackgroundStyleChange)
                },
            )
            Text(
                stringResource(if (customBackgroundMode == "video") R.string.settings_bg_style_video_detail
                    else R.string.settings_bg_style_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (customBackgroundMode != "default" && !customBackgroundUri.isNullOrBlank() && !backgroundDisabled) SettingsDisclosureRow(
            title = stringResource(R.string.settings_advanced),
            detail = stringResource(R.string.settings_advanced_detail),
            expanded = customBackgroundAdvancedExpanded,
            onExpandedChange = { customBackgroundAdvancedExpanded = it },
        )
        if (backgroundDisabled) {
            Text(stringResource(R.string.settings_bg_video_disabled), style = MaterialTheme.typography.bodySmall)
        }
        if (customBackgroundAdvancedExpanded && customBackgroundMode != "default" &&
            !customBackgroundUri.isNullOrBlank() && !backgroundDisabled) {
            if (customBackgroundMode == "image") {
                SettingsSliderRow(
                    title = stringResource(R.string.settings_blur),
                    valueLabel = { "${it.roundToInt()} dp" },
                    value = customBackgroundBlur.coerceIn(0f, maxBlur),
                    valueRange = 0f..maxBlur,
                    steps = maxBlur.toInt() - 1,
                    onValueChange = onCustomBackgroundBlurChange,
                )
            }
            SettingsSliderRow(
                title = stringResource(R.string.settings_brightness),
                valueLabel = { "${(it * 100f).roundToInt()}%" },
                value = customBackgroundBrightness,
                valueRange = 0.35f..1.15f,
                steps = 15,
                onValueChange = onCustomBackgroundBrightnessChange,
            )
            SettingsSliderRow(
                title = stringResource(R.string.settings_glass),
                valueLabel = { "${(it * 100f).roundToInt()}%" },
                value = customBackgroundGlass,
                valueRange = 0.08f..0.90f,
                steps = 13,
                onValueChange = onCustomBackgroundGlassChange,
            )
            SettingsSliderRow(
                title = stringResource(R.string.settings_scale),
                valueLabel = { "${(it * 100f).roundToInt()}%" },
                value = customBackgroundScale,
                valueRange = 1.00f..1.40f,
                steps = 15,
                onValueChange = onCustomBackgroundScaleChange,
            )
        }
    }

    SettingsSectionCard(
        title = stringResource(R.string.settings_section_fonts),
    ) {
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
        SettingsSliderRow(
            title = stringResource(R.string.settings_ui_font_size),
            valueLabel = { "${(it * 100f).roundToInt()}%" },
            value = uiFontScale,
            valueRange = 0.88f..1.18f,
            steps = 14,
            onValueChange = onUiFontScaleChange,
        )
        SettingsSliderRow(
            title = stringResource(R.string.settings_ui_density),
            valueLabel = { "${(it * 100f).roundToInt()}%" },
            value = uiDensityScale,
            valueRange = 0.90f..1.12f,
            steps = 10,
            onValueChange = onUiDensityScaleChange,
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
        SettingsSliderRow(
            title = stringResource(R.string.settings_lyrics_font_size),
            valueLabel = { "${(it * 100f).roundToInt()}%" },
            value = lyricsFontScale,
            valueRange = 0.82f..1.28f,
            steps = 22,
            onValueChange = onLyricsFontScaleChange,
        )
        SettingsActionRow(
            title = stringResource(R.string.settings_reselect_font),
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
            onClick = onClearImportedFont,
        )
    }
}
