package app.echo.android.feature.settings

import app.echo.android.model.settings.EchoCustomColors
import app.echo.android.model.settings.EchoSavedColorTheme
import app.echo.android.model.settings.EchoSavedColorThemeResult

/** What the theme gallery shows: the chosen palette, its custom seeds and the saved custom themes. */
data class SettingsPaletteState(
    val colorTheme: String,
    val customColors: EchoCustomColors,
    val savedColorThemes: List<EchoSavedColorTheme>,
    val appliedSavedColorThemeId: String?,
    val dynamicColorEnabled: Boolean,
)

data class SettingsPaletteActions(
    val onColorThemeChange: (String) -> Unit,
    val onCustomColorsChange: (EchoCustomColors) -> Unit,
    val onSaveCustomColorTheme: (String, (EchoSavedColorThemeResult) -> Unit) -> Unit,
    val onApplySavedColorTheme: (String) -> Unit,
    val onDeleteSavedColorTheme: (String) -> Unit,
    val onDynamicColorEnabledChange: (Boolean) -> Unit,
)
