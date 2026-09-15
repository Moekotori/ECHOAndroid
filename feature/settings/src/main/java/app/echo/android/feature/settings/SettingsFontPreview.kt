package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.echoFontFamilyForMode

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
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = scheme.primary.copy(alpha = 0.06f),
        contentColor = scheme.onSurface,
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Explicit base sizes avoid applying the app's font scale twice.
            Text(
                stringResource(if (lyrics) R.string.settings_font_preview_lyrics else R.string.settings_font_preview_ui),
                style = TextStyle(
                    fontFamily = family,
                    fontWeight = if (lyrics) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = ((if (lyrics) 22f else 16f) * scale).sp,
                    lineHeight = ((if (lyrics) 30f else 24f) * scale).sp,
                ),
            )
            Text(
                stringResource(R.string.settings_font_preview_characters),
                color = scheme.onSurfaceVariant,
                style = TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (12f * scale).sp,
                    lineHeight = (18f * scale).sp,
                ),
            )
        }
    }
}
