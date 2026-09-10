package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
internal fun SettingsInterfaceContent(
    dynamicArtworkEnabled: Boolean,
    compactModeEnabled: Boolean,
    performanceMode: String,
    effectivePerformanceMode: String,
    appLanguage: String,
    onDynamicArtworkEnabledChange: (Boolean) -> Unit,
    onCompactModeEnabledChange: (Boolean) -> Unit,
    onPerformanceModeChange: (String) -> Unit,
    onAppLanguageChange: (String) -> Unit,
) {
    SettingsSectionCard(
        title = stringResource(R.string.settings_section_interface),
    ) {
        SettingsLanguageRow(appLanguage, onAppLanguageChange)
        SettingsChoiceGroupRow(
            title = stringResource(R.string.settings_performance_mode),
            detail = performanceModeDetail(performanceMode, effectivePerformanceMode),
            options = performanceModeOptions(),
            selectedValue = performanceMode,
            onOptionSelected = onPerformanceModeChange,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_dynamic_artwork),
            detail = stringResource(R.string.settings_dynamic_artwork_detail),
            checked = dynamicArtworkEnabled,
            onCheckedChange = onDynamicArtworkEnabledChange,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_compact_mode),
            detail = stringResource(R.string.settings_compact_mode_detail),
            checked = compactModeEnabled,
            onCheckedChange = onCompactModeEnabledChange,
        )
    }
}
