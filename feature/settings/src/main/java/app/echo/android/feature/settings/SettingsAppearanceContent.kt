package app.echo.android.feature.settings

import app.echo.android.design.backgroundMaxBlur
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.model.settings.EchoBackgroundStyle
import kotlin.math.roundToInt

@Composable
internal fun SettingsAppearanceContent(
    importedFontFamily: FontFamily?,
    customBackgroundMode: String,
    customBackgroundUri: String?,
    startupBackgroundUri: String?,
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
    onPickImageBackground: () -> Unit,
    onPickStartupBackground: () -> Unit,
    onClearStartupBackground: () -> Unit,
    onPickVideoBackground: () -> Unit,
    onClearCustomBackground: () -> Unit,
    onCustomBackgroundBlurChange: (Float) -> Unit,
    onCustomBackgroundBrightnessChange: (Float) -> Unit,
    onCustomBackgroundGlassChange: (Float) -> Unit,
    onCustomBackgroundScaleChange: (Float) -> Unit,
    onCustomBackgroundStyleChange: (EchoBackgroundStyle) -> Unit,
    onUiFontFamilyChange: (String) -> Unit,
    onUiFontScaleChange: (Float) -> Unit,
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
    Column(
        modifier = Modifier.fillMaxWidth()
            .settingsSearchAnchor(stringResource(R.string.settings_section_theme))
            .padding(horizontal = SettingsContentInset),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsAppearancePreview(uiFontFamily, importedFontFamily, uiFontScale, uiDensityScale)
        ThemeModeSelector(
            selectedMode = themeMode,
            onSelect = onThemeModeChange,
        )
    }

    val backgroundDisabled = customBackgroundMode == "video" &&
        LocalEchoEffectivePerformanceMode.current.isLightweight
    val maxBlur = LocalEchoEffectivePerformanceMode.current.backgroundMaxBlur
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
        if (backgroundDisabled) {
            Text(stringResource(R.string.settings_bg_video_disabled), style = MaterialTheme.typography.bodySmall)
        }

    }

    if (customBackgroundMode != "default" && !customBackgroundUri.isNullOrBlank() && !backgroundDisabled) {
        SettingsSectionCard(title = stringResource(R.string.settings_section_background_adjustments), secondary = true) {
            SettingsBackgroundAdjustments(
                uri = customBackgroundUri,
                isVideo = customBackgroundMode == "video",
                blur = customBackgroundBlur.coerceIn(0f, maxBlur),
                brightness = customBackgroundBrightness,
                glass = customBackgroundGlass,
                scale = customBackgroundScale,
                maxBlur = maxBlur,
                onBlurChange = onCustomBackgroundBlurChange,
                onBrightnessChange = onCustomBackgroundBrightnessChange,
                onGlassChange = onCustomBackgroundGlassChange,
                onScaleChange = onCustomBackgroundScaleChange,
            )
        }
    }

    SettingsTypographyContent(
        importedFontFamily, uiFontFamily, uiFontScale, lyricsFontFamily, lyricsFontScale,
        importedFontUri, onUiFontFamilyChange, onUiFontScaleChange,
        onLyricsFontFamilyChange, onLyricsFontScaleChange,
        onImportUiFont, onImportLyricsFont, onClearImportedFont,
    )

    SettingsSectionCard(title = stringResource(R.string.settings_section_schedule), secondary = true) {
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

    SettingsStartupBackgroundCard(
        uri = startupBackgroundUri,
        onPick = onPickStartupBackground,
        onClear = onClearStartupBackground,
    )

}
