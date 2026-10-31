package app.echo.android.feature.settings

import app.echo.android.model.settings.EchoThemeMode
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@Composable
internal fun ThemeModeSelector(
    selectedMode: String,
    onSelect: (String) -> Unit,
) {
    val options = listOf(
        EchoThemeMode.Light to R.string.settings_theme_light,
        EchoThemeMode.Dark to R.string.settings_theme_dark,
        EchoThemeMode.System to R.string.settings_theme_system,
    )
    SettingsSegmentedControl(
        segments = options.map { (mode, label) ->
            SettingsSegment(stringResource(label), selected = selectedMode == mode, onClick = { onSelect(mode) })
        },
        modifier = Modifier.settingsSearchAnchor(stringResource(R.string.settings_display_mode)),
    )
}
