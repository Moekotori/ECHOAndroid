package app.echo.android.feature.settings

import app.echo.android.model.settings.EchoFontFamilyMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.echo.android.design.echoClickable
import androidx.compose.ui.semantics.Role
import kotlin.math.roundToInt

@Composable
internal fun SettingsTypographyContent(
    importedFontFamily: FontFamily?,
    uiFontFamily: String,
    uiFontScale: Float,
    uiDensityScale: Float,
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
    showHeader: Boolean = true,
) {
    // Drafts follow the sliders live; the saved values only change on release.
    var draftUi by remember(uiFontScale) { mutableFloatStateOf(uiFontScale) }
    var draftLyrics by remember(lyricsFontScale) { mutableFloatStateOf(lyricsFontScale) }
    val hasImported = !importedFontUri.isNullOrBlank()
    SettingsSectionCard(title = stringResource(R.string.settings_section_readability), showHeader = showHeader) {
        Column(
            Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsAppearancePreview(
                uiFontMode = uiFontFamily,
                lyricsFontMode = lyricsFontFamily,
                importedFontFamily = importedFontFamily,
                uiScale = draftUi,
                lyricsScale = draftLyrics,
                densityScale = uiDensityScale,
            )
            Column(Modifier.settingsSearchAnchor(stringResource(R.string.settings_font_advanced))) {
                SettingsChipGroup(
                    title = stringResource(R.string.settings_ui_font),
                    options = fontOptions(importedFontUri),
                    selectedValue = uiFontFamily,
                    onOptionSelected = { value ->
                        if (value == EchoFontFamilyMode.Imported && !hasImported) onImportUiFont()
                        else onUiFontFamilyChange(value)
                    },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.settings_ui_font_size),
                    valueLabel = { "${(it * 100f).roundToInt()}%" },
                    value = uiFontScale,
                    valueRange = 0.88f..1.18f,
                    steps = 14,
                    onValueChange = onUiFontScaleChange,
                    onPreviewValueChange = { draftUi = it },
                    dense = true,
                    neutralValue = 1f,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Column {
                SettingsChipGroup(
                    title = stringResource(R.string.settings_lyrics_font),
                    options = fontOptions(importedFontUri),
                    selectedValue = lyricsFontFamily,
                    onOptionSelected = { value ->
                        if (value == EchoFontFamilyMode.Imported && !hasImported) onImportLyricsFont()
                        else onLyricsFontFamilyChange(value)
                    },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.settings_lyrics_font_size),
                    valueLabel = { "${(it * 100f).roundToInt()}%" },
                    value = lyricsFontScale,
                    valueRange = 0.82f..1.28f,
                    steps = 22,
                    onValueChange = onLyricsFontScaleChange,
                    onPreviewValueChange = { draftLyrics = it },
                    dense = true,
                    neutralValue = 1f,
                )
            }
        }
        FontFileRow(importedFontUri, onImport = onImportUiFont, onClear = onClearImportedFont)
    }
}

/** Import and clear share one row so the font file needs no second block. */
@Composable
private fun FontFileRow(
    importedFontUri: String?,
    onImport: () -> Unit,
    onClear: () -> Unit,
) {
    val hasImported = !importedFontUri.isNullOrBlank()
    SettingsRowShell(
        title = stringResource(if (hasImported) R.string.settings_reselect_font else R.string.settings_import_font),
        detail = if (hasImported) {
            stringResource(R.string.settings_import_font_current, importedFontUri.orEmpty().substringAfterLast('/').takeLast(28))
        } else {
            stringResource(R.string.settings_import_font_detail)
        },
        modifier = Modifier.echoClickable(role = Role.Button, onClick = onImport),
    ) {
        if (hasImported) TextButton(onClick = onClear) {
            Text(stringResource(R.string.settings_clear_action), maxLines = 1)
        }
    }
}
