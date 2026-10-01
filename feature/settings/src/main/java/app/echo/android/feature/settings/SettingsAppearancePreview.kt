package app.echo.android.feature.settings

import app.echo.android.design.EchoIcon

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.echoFontFamilyForMode
import app.echo.android.design.echoTypography

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
    val typography = remember(family, fontScale) { echoTypography(family, fontScale) }
    val spacing = densityScale.coerceIn(0.90f, 1.12f)
    val previewLabel = stringResource(R.string.settings_appearance_preview)
    Column(Modifier.fillMaxWidth().semantics { contentDescription = previewLabel }) {
        Row(Modifier.fillMaxWidth().padding(vertical = (8f * spacing).dp),
            horizontalArrangement = Arrangement.spacedBy((12f * spacing).dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size((40f * spacing).dp)
                .background(scheme.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                EchoIcon(Icons.Rounded.MusicNote, null, tint = scheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((4f * spacing).dp)) {
                Text(stringResource(R.string.settings_font_preview_ui),
                    style = typography.titleMedium,
                    color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.settings_font_preview_characters),
                    style = typography.bodySmall, color = scheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            EchoIcon(Icons.Rounded.PlayArrow, null, tint = scheme.primary)
        }
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.4f))
    }
}
