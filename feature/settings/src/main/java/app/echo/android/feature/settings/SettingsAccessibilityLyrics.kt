package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt

@Composable
internal fun SettingsAccessibilityLyrics(
    lineSpacing: Float,
    showTranslation: Boolean,
    showRomanization: Boolean,
    onLineSpacingChange: (Float) -> Unit,
    onShowTranslationChange: (Boolean) -> Unit,
    onShowRomanizationChange: (Boolean) -> Unit,
) {
    SettingsSectionCard(title = stringResource(R.string.accessibility_lyrics_reading)) {
        SettingsSliderRow(
            title = stringResource(R.string.accessibility_lyrics_spacing),
            valueLabel = { "${(it * 100f).roundToInt()}%" },
            value = lineSpacing,
            valueRange = 0.5f..1.38f,
            steps = 43,
            onValueChange = onLineSpacingChange,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.accessibility_translation),
            detail = stringResource(R.string.accessibility_translation_detail),
            checked = showTranslation,
            onCheckedChange = onShowTranslationChange,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.accessibility_romanization),
            detail = stringResource(R.string.accessibility_romanization_detail),
            checked = showRomanization,
            onCheckedChange = onShowRomanizationChange,
        )
    }
}
