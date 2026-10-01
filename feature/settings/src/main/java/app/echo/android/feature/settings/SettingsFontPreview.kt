package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.echoFontFamilyForMode
import app.echo.android.design.echoTypography

/** Uses the same resolved font as playback, with a local draft size while dragging. */
@Composable
internal fun SettingsFontPreview(
    mode: String,
    importedFontFamily: FontFamily?,
    scale: Float,
    lyrics: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val family = echoFontFamilyForMode(mode, importedFontFamily)
    val typography = remember(family, scale) { echoTypography(family, scale) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        shape = SettingsShape,
        color = Color.Transparent,
        contentColor = scheme.onSurface,
    ) {
        Column(
            Modifier.padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Use the draft scale rather than applying the app's scale a second time.
            Text(
                stringResource(if (lyrics) R.string.settings_font_preview_lyrics else R.string.settings_font_preview_ui),
                style = if (lyrics) TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.Bold,
                    fontSize = (22f * scale).sp,
                    lineHeight = (30f * scale).sp,
                ) else typography.titleMedium,
            )
            Text(
                stringResource(R.string.settings_font_preview_characters),
                color = scheme.onSurfaceVariant,
                style = if (lyrics) TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.Normal,
                    fontSize = (12f * scale).sp,
                    lineHeight = (18f * scale).sp,
                ) else typography.bodySmall,
            )
        }
    }
}
