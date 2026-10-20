package app.echo.android.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import app.echo.android.design.EchoStateContent
import app.echo.android.design.echoPageSection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.echoClickable
import app.echo.android.design.echoTheme

internal enum class LyricsSettingsCategory(val titleRes: Int, val icon: ImageVector) {
    Appearance(R.string.player_appearance_title, Icons.Rounded.Palette),
    Backdrop(R.string.lyrics_backdrop_title, Icons.Rounded.Wallpaper),
    Typography(R.string.lyrics_setting_typography, Icons.Rounded.TextFields),
    Color(R.string.lyrics_setting_color_background, Icons.Rounded.ColorLens),
    Motion(R.string.lyrics_setting_motion, Icons.Rounded.Lyrics),
    Content(R.string.lyrics_setting_content, Icons.Rounded.Translate),
}

/** The overview exposes destinations, while only the selected controls are composed. */
@Composable
internal fun LyricsSettingsMenu(
    appearanceSummary: String,
    backdropSummary: String,
    typographySummary: String,
    colorSummary: String,
    motionSummary: String,
    onSelect: (LyricsSettingsCategory) -> Unit,
) {
    val theme = echoTheme()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LyricsSettingsCategory.entries.forEachIndexed { index, category ->
            val summary = when (category) {
                LyricsSettingsCategory.Backdrop -> backdropSummary
                LyricsSettingsCategory.Typography -> typographySummary
                LyricsSettingsCategory.Color -> colorSummary
                LyricsSettingsCategory.Motion -> motionSummary
                LyricsSettingsCategory.Content -> stringResource(R.string.lyrics_setting_content_summary)
                LyricsSettingsCategory.Appearance -> appearanceSummary
            }
            Row(
                Modifier.echoPageSection(minOf(index, 3)).fillMaxWidth().clip(PlaybackRowShape)
                    .echoClickable(role = Role.Button, onClick = { onSelect(category) })
                    .heightIn(min = PlaybackRowMinHeight).padding(horizontal = PlaybackRowInset, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PlaybackRowBadge(category.icon)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(category.titleRes), style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium, color = theme.heading)
                    if (summary.isNotBlank()) EchoStateContent(summary) {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = theme.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                EchoIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                    tint = theme.muted, modifier = Modifier.size(20.dp))
            }
        }
    }
}
