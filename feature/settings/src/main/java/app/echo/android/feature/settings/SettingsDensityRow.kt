package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

/** One layout preference replaces the separate spacing switch and density slider. */
@Composable
internal fun SettingsDensityRow(
    compactModeEnabled: Boolean,
    uiDensityScale: Float,
    onCompactModeEnabledChange: (Boolean) -> Unit,
    onUiDensityScaleChange: (Float) -> Unit,
) {
    val selected = when {
        compactModeEnabled && abs(uiDensityScale - 0.95f) < 0.001f -> "compact"
        !compactModeEnabled && abs(uiDensityScale - 1f) < 0.001f -> "standard"
        !compactModeEnabled && abs(uiDensityScale - 1.08f) < 0.001f -> "comfortable"
        else -> ""
    }
    val current = "${(uiDensityScale * 100f).roundToInt()}%" +
        if (compactModeEnabled) " · " + stringResource(R.string.settings_compact_mode) else ""
    SettingsChoiceGroupRow(
        title = stringResource(R.string.settings_ui_density),
        detail = if (selected.isEmpty()) stringResource(R.string.settings_density_custom, current) else current,
        options = listOf(
            SettingsChoiceOption("compact", stringResource(R.string.settings_compact_mode)),
            SettingsChoiceOption("standard", stringResource(R.string.settings_density_standard)),
            SettingsChoiceOption("comfortable", stringResource(R.string.settings_density_comfortable)),
        ),
        selectedValue = selected,
        onOptionSelected = { value ->
            val scale = when (value) {
                "compact" -> 0.95f
                "comfortable" -> 1.08f
                else -> 1f
            }
            onCompactModeEnabledChange(value == "compact")
            onUiDensityScaleChange(scale)
        },
    )
}
