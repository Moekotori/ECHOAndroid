package app.echo.android.feature.settings

import app.echo.android.design.EchoIcon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.echoFontFamilyForMode
import app.echo.android.design.echoTypography

/**
 * One card shows both the interface and the lyrics text with the draft fonts and sizes,
 * so dragging either slider answers "how will it look" without scrolling back.
 */
@Composable
internal fun SettingsAppearancePreview(
    uiFontMode: String,
    lyricsFontMode: String,
    importedFontFamily: FontFamily?,
    uiScale: Float,
    lyricsScale: Float,
    densityScale: Float,
) {
    val scheme = MaterialTheme.colorScheme
    val uiFamily = echoFontFamilyForMode(uiFontMode, importedFontFamily)
    val lyricsFamily = echoFontFamilyForMode(lyricsFontMode, importedFontFamily)
    val typography = remember(uiFamily, uiScale) { echoTypography(uiFamily, uiScale) }
    val spacing = densityScale.coerceIn(0.90f, 1.12f)
    val previewLabel = stringResource(R.string.settings_appearance_preview)
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = previewLabel },
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = (12f * spacing).dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy((12f * spacing).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size((44f * spacing).dp).background(scheme.primaryContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    EchoIcon(Icons.Rounded.MusicNote, null, tint = scheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((3f * spacing).dp)) {
                    Text(
                        stringResource(R.string.settings_font_preview_ui),
                        style = typography.titleMedium, color = scheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.settings_font_preview_characters),
                        style = typography.bodySmall, color = scheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                EchoIcon(Icons.Rounded.PlayArrow, null, tint = scheme.onSurfaceVariant)
            }
            HorizontalDivider(
                Modifier.padding(vertical = (10f * spacing).dp),
                color = scheme.outlineVariant.copy(alpha = 0.5f),
            )
            // The draft lyrics scale is applied here directly, not on top of the app's saved scale.
            Text(
                stringResource(R.string.settings_font_preview_lyrics),
                style = TextStyle(
                    fontFamily = lyricsFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = (22f * lyricsScale).sp,
                    lineHeight = (30f * lyricsScale).sp,
                ),
                color = scheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.settings_font_preview_characters),
                style = TextStyle(
                    fontFamily = lyricsFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = (14f * lyricsScale).sp,
                    lineHeight = (20f * lyricsScale).sp,
                ),
                color = scheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
