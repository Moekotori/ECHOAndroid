package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.echoFontFamilyForMode

/** A music row shows the chosen colors, font and spacing without rebuilding a real library. */
@Composable
internal fun SettingsAppearancePreview(
    fontMode: String,
    importedFontFamily: FontFamily?,
    fontScale: Float,
    densityScale: Float,
) {
    val scheme = MaterialTheme.colorScheme
    val family = echoFontFamilyForMode(fontMode, importedFontFamily)
    val spacing = densityScale.coerceIn(0.90f, 1.12f)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = (8f * spacing).dp),
            horizontalArrangement = Arrangement.spacedBy((12f * spacing).dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size((40f * spacing).dp).background(scheme.primaryContainer),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, tint = scheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((4f * spacing).dp)) {
                Text(stringResource(R.string.settings_font_preview_ui),
                    style = TextStyle(fontFamily = family, fontSize = (16f * fontScale).sp,
                        lineHeight = (22f * fontScale).sp, fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.settings_font_preview_characters),
                    style = TextStyle(fontFamily = family, fontSize = (12f * fontScale).sp,
                        lineHeight = (18f * fontScale).sp), color = scheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.PlayArrow, null, tint = scheme.primary)
        }
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.4f))
    }
}
