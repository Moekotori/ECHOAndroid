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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.echoClickable
import app.echo.android.design.echoTheme

internal enum class LyricsSettingsCategory(val titleRes: Int, val icon: ImageVector) {
    Appearance(R.string.player_appearance_title, Icons.Rounded.Palette),
    Typography(R.string.lyrics_setting_typography, Icons.Rounded.TextFields),
    Color(R.string.lyrics_setting_color_background, Icons.Rounded.ColorLens),
    Motion(R.string.lyrics_setting_motion, Icons.Rounded.Lyrics),
    Content(R.string.lyrics_setting_content, Icons.Rounded.Translate),
}

/** The overview exposes destinations, while only the selected controls are composed. */
@Composable
internal fun LyricsSettingsMenu(
    typographySummary: String,
    colorSummary: String,
    motionSummary: String,
    onSelect: (LyricsSettingsCategory) -> Unit,
) {
    val theme = echoTheme()
    Column(Modifier.fillMaxWidth()) {
        LyricsSettingsCategory.entries.forEachIndexed { index, category ->
            val summary = when (category) {
                LyricsSettingsCategory.Typography -> typographySummary
                LyricsSettingsCategory.Color -> colorSummary
                LyricsSettingsCategory.Motion -> motionSummary
                LyricsSettingsCategory.Content -> stringResource(R.string.lyrics_setting_content_summary)
                LyricsSettingsCategory.Appearance -> ""
            }
            Row(
                Modifier.fillMaxWidth().echoClickable(onClick = { onSelect(category) })
                    .heightIn(min = 60.dp).padding(horizontal = 2.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EchoIcon(category.icon, null, tint = theme.heading, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(category.titleRes), style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium, color = theme.heading)
                    if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.bodySmall,
                        color = theme.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                EchoIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                    tint = theme.muted, modifier = Modifier.size(18.dp))
            }
            if (index < LyricsSettingsCategory.entries.lastIndex) {
                HorizontalDivider(color = theme.muted.copy(alpha = 0.12f))
            }
        }
    }
}
