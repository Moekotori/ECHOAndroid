package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
internal fun SettingsLibraryContent(
    trackAudioInfoTagsVisible: Boolean,
    onTrackAudioInfoTagsVisibleChange: (Boolean) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    SettingsSectionCard(
        title = stringResource(R.string.settings_section_library),
    ) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_audio_tags),
            detail = stringResource(R.string.settings_audio_tags_detail),
            checked = trackAudioInfoTagsVisible,
            onCheckedChange = onTrackAudioInfoTagsVisibleChange,
        )
        SettingsActionRow(
            title = stringResource(R.string.settings_local_music),
            detail = stringResource(R.string.settings_local_music_detail),
            onClick = onOpenLibrary,
        )
    }
}
