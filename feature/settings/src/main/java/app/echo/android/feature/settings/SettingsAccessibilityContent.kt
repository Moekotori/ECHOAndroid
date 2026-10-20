package app.echo.android.feature.settings

import app.echo.android.model.settings.EchoLyricsMotionMode
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily

@Composable
internal fun SettingsAccessibilityContent(
    isActive: Boolean,
    importedFontFamily: FontFamily?,
    uiFontFamily: String,
    uiFontScale: Float,
    lyricsFontFamily: String,
    lyricsFontScale: Float,
    compactModeEnabled: Boolean,
    lyricsWordHighlightEnabled: Boolean,
    lyricsMotionMode: String,
    playbackHapticsEnabled: Boolean,
    onUiFontScaleChange: (Float) -> Unit,
    onLyricsFontScaleChange: (Float) -> Unit,
    onCompactModeEnabledChange: (Boolean) -> Unit,
    onLyricsWordHighlightEnabledChange: (Boolean) -> Unit,
    onLyricsMotionModeChange: (String) -> Unit,
    onPlaybackHapticsEnabledChange: (Boolean) -> Unit,
    careContent: @Composable () -> Unit = {},
) {
    Text(
        stringResource(R.string.accessibility_welcome),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = SettingsContentInset, vertical = 4.dp),
    )
    SettingsSectionCard(title = stringResource(R.string.settings_section_readability)) {
        SettingsReadabilityControls(
            importedFontFamily, uiFontFamily, uiFontScale, lyricsFontFamily, lyricsFontScale,
            onUiFontScaleChange, onLyricsFontScaleChange,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.accessibility_spacious_layout),
            detail = stringResource(R.string.accessibility_spacious_layout_detail),
            checked = !compactModeEnabled,
            onCheckedChange = { onCompactModeEnabledChange(!it) },
        )
    }
    SettingsSectionCard(title = stringResource(R.string.accessibility_motion_feedback)) {
        SettingsSwitchRow(
            title = stringResource(R.string.accessibility_word_highlight),
            detail = stringResource(R.string.accessibility_word_highlight_detail),
            checked = lyricsWordHighlightEnabled,
            onCheckedChange = onLyricsWordHighlightEnabledChange,
        )
        SettingsChoiceGroupRow(
            title = stringResource(R.string.accessibility_lyrics_motion),
            detail = stringResource(R.string.accessibility_lyrics_motion_detail),
            options = listOf(
                SettingsChoiceOption(EchoLyricsMotionMode.Calm, stringResource(R.string.accessibility_motion_calm)),
                SettingsChoiceOption(EchoLyricsMotionMode.Smooth, stringResource(R.string.accessibility_motion_smooth)),
                SettingsChoiceOption(EchoLyricsMotionMode.Stage, stringResource(R.string.accessibility_motion_stage)),
            ),
            selectedValue = lyricsMotionMode,
            onOptionSelected = onLyricsMotionModeChange,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_playback_haptics),
            detail = stringResource(R.string.settings_playback_haptics_detail),
            checked = playbackHapticsEnabled,
            onCheckedChange = onPlaybackHapticsEnabledChange,
        )
    }
    careContent()
    SettingsAccessibilitySystemContent(isActive)
}
