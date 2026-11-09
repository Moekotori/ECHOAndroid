package app.echo.android.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

internal enum class LyricsSettingsCategory(val titleRes: Int, val icon: ImageVector) {
    Appearance(R.string.player_appearance_title, Icons.Rounded.Palette),
    Backdrop(R.string.lyrics_backdrop_title, Icons.Rounded.Wallpaper),
    Typography(R.string.lyrics_setting_typography, Icons.Rounded.TextFields),
    Color(R.string.lyrics_setting_color_background, Icons.Rounded.ColorLens),
    Motion(R.string.lyrics_setting_motion, Icons.Rounded.Lyrics),
    WordStyle(R.string.lyrics_word_style_title, Icons.Rounded.AutoAwesome),
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
    wordStyleSummary: String,
    onSelect: (LyricsSettingsCategory) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LyricsSettingsCategory.entries.forEachIndexed { index, category ->
            val summary = when (category) {
                LyricsSettingsCategory.Backdrop -> backdropSummary
                LyricsSettingsCategory.Typography -> typographySummary
                LyricsSettingsCategory.Color -> colorSummary
                LyricsSettingsCategory.Motion -> motionSummary
                LyricsSettingsCategory.WordStyle -> wordStyleSummary
                LyricsSettingsCategory.Content -> stringResource(R.string.lyrics_setting_content_summary)
                LyricsSettingsCategory.Appearance -> appearanceSummary
            }
            PlaybackActionRow(
                icon = category.icon,
                text = stringResource(category.titleRes),
                detail = summary,
                onClick = { onSelect(category) },
                framed = false,
                compact = true,
                revealOrder = minOf(index + 1, 3),
            )
        }
    }
}
