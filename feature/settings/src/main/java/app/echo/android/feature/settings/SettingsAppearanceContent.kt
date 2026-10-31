package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.echo.android.model.settings.EchoBackgroundMode
import app.echo.android.design.LocalEchoPlatformCapabilities
import app.echo.android.model.settings.EchoBackgroundStyle
import app.echo.android.model.settings.EchoColorTheme

/**
 * Single pane: a short list of sub-pages (theme, text, background). Two panes have no room to
 * navigate, so they show every section inline in the same order.
 */
@Composable
internal fun SettingsAppearanceContent(
    subNav: SettingsSubNav,
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
    palette: SettingsPaletteState,
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
    paletteActions: SettingsPaletteActions,
    onScheduledDarkModeEnabledChange: (Boolean) -> Unit,
    onScheduledDarkStartMinuteChange: (Int) -> Unit,
    onScheduledDarkEndMinuteChange: (Int) -> Unit,
) {
    val onOpen = subNav.open
    val page = subNav.page
    if (onOpen != null && page == null) {
        SettingsAppearanceHub(
            themeMode = themeMode,
            paletteLabel = colorThemeLabel(palette.colorTheme),
            uiFontScale = uiFontScale,
            lyricsFontScale = lyricsFontScale,
            backgroundMode = customBackgroundMode,
            hasBackground = customBackgroundMode != EchoBackgroundMode.Default && !customBackgroundUri.isNullOrBlank(),
            onOpen = onOpen,
        )
        return
    }
    // Inline layouts keep the section headings; a sub-page already has its title in the header.
    val showHeader = onOpen == null
    if (page == null || page == SettingsSubPage.AppearanceTheme) {
        SettingsSectionCard(title = stringResource(R.string.settings_section_theme), showHeader = showHeader) {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ThemeModeSelector(selectedMode = themeMode, onSelect = onThemeModeChange)
                ThemePaletteSelector(
                    selectedId = palette.colorTheme,
                    customColors = palette.customColors,
                    onSelect = paletteActions.onColorThemeChange,
                )
                ThemeCustomEditor(
                    selected = palette.colorTheme == EchoColorTheme.Custom.id,
                    colors = palette.customColors,
                    savedThemes = palette.savedColorThemes,
                    appliedId = palette.appliedSavedColorThemeId,
                    onColorsChange = paletteActions.onCustomColorsChange,
                    onSave = paletteActions.onSaveCustomColorTheme,
                    onApply = paletteActions.onApplySavedColorTheme,
                    onDelete = paletteActions.onDeleteSavedColorTheme,
                )
                val dynamicColorAvailable = LocalEchoPlatformCapabilities.current.dynamicColor
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    detail = stringResource(
                        if (dynamicColorAvailable) R.string.settings_dynamic_color_detail
                        else R.string.settings_dynamic_color_unavailable,
                    ),
                    checked = palette.dynamicColorEnabled && dynamicColorAvailable,
                    onCheckedChange = paletteActions.onDynamicColorEnabledChange,
                    enabled = dynamicColorAvailable,
                )
                SettingsScheduleDarkContent(
                    enabled = scheduledDarkModeEnabled,
                    startMinute = scheduledDarkStartMinute,
                    endMinute = scheduledDarkEndMinute,
                    onEnabledChange = onScheduledDarkModeEnabledChange,
                    onStartMinuteChange = onScheduledDarkStartMinuteChange,
                    onEndMinuteChange = onScheduledDarkEndMinuteChange,
                )
            }
        }
    }

    if (page == null || page == SettingsSubPage.AppearanceText) {
        SettingsTypographyContent(
            importedFontFamily = importedFontFamily,
            uiFontFamily = uiFontFamily,
            uiFontScale = uiFontScale,
            uiDensityScale = uiDensityScale,
            lyricsFontFamily = lyricsFontFamily,
            lyricsFontScale = lyricsFontScale,
            importedFontUri = importedFontUri,
            onUiFontFamilyChange = onUiFontFamilyChange,
            onUiFontScaleChange = onUiFontScaleChange,
            onLyricsFontFamilyChange = onLyricsFontFamilyChange,
            onLyricsFontScaleChange = onLyricsFontScaleChange,
            onImportUiFont = onImportUiFont,
            onImportLyricsFont = onImportLyricsFont,
            onClearImportedFont = onClearImportedFont,
            showHeader = showHeader,
        )
    }

    if (page == null || page == SettingsSubPage.AppearanceBackground) {
        SettingsBackgroundContent(
            mode = customBackgroundMode,
            uri = customBackgroundUri,
            startupUri = startupBackgroundUri,
            blur = customBackgroundBlur,
            brightness = customBackgroundBrightness,
            glass = customBackgroundGlass,
            scale = customBackgroundScale,
            onPickImage = onPickImageBackground,
            onPickVideo = onPickVideoBackground,
            onClear = onClearCustomBackground,
            onPickStartup = onPickStartupBackground,
            onClearStartup = onClearStartupBackground,
            onBlurChange = onCustomBackgroundBlurChange,
            onBrightnessChange = onCustomBackgroundBrightnessChange,
            onGlassChange = onCustomBackgroundGlassChange,
            onScaleChange = onCustomBackgroundScaleChange,
            onStyleChange = onCustomBackgroundStyleChange,
            showHeader = showHeader,
        )
    }
}
