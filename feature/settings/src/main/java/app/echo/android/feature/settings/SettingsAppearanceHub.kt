package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.settings.EchoBackgroundMode
import app.echo.android.model.settings.EchoThemeMode
import kotlin.math.roundToInt

/** Entry list for the Appearance sub-pages; each row shows the current value, not help text. */
@Composable
internal fun SettingsAppearanceHub(
    themeMode: String,
    paletteLabel: String,
    uiFontScale: Float,
    lyricsFontScale: Float,
    backgroundMode: String,
    hasBackground: Boolean,
    onOpen: (SettingsSubPage) -> Unit,
) {
    val compact = LocalSettingsCompactMode.current
    val themeSummary = stringResource(
        when (themeMode) {
            EchoThemeMode.Dark -> R.string.settings_theme_dark
            EchoThemeMode.Light -> R.string.settings_theme_light
            else -> R.string.settings_theme_system
        },
    )
    val backgroundSummary = stringResource(
        when {
            !hasBackground -> R.string.settings_bg_default_label
            backgroundMode == EchoBackgroundMode.Video -> R.string.settings_bg_video_label
            else -> R.string.settings_bg_image_label
        },
    )
    Column {
        SettingsMenuRow(
            icon = Icons.Rounded.Contrast,
            title = stringResource(SettingsSubPage.AppearanceTheme.title),
            summary = "$themeSummary · $paletteLabel",
            compactMode = compact,
            onClick = { onOpen(SettingsSubPage.AppearanceTheme) },
        )
        SettingsMenuRow(
            icon = Icons.Rounded.TextFields,
            title = stringResource(SettingsSubPage.AppearanceText.title),
            summary = "${(uiFontScale * 100f).roundToInt()}% · ${(lyricsFontScale * 100f).roundToInt()}%",
            compactMode = compact,
            onClick = { onOpen(SettingsSubPage.AppearanceText) },
        )
        SettingsMenuRow(
            icon = Icons.Rounded.Wallpaper,
            title = stringResource(SettingsSubPage.AppearanceBackground.title),
            summary = backgroundSummary,
            compactMode = compact,
            onClick = { onOpen(SettingsSubPage.AppearanceBackground) },
        )
    }
}
