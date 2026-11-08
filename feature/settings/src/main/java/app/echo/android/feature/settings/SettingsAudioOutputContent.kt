package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Interruption and connection preferences apply to every local output. */
@Composable
internal fun SettingsAudioOutputContent(
    audioFocusEnabled: Boolean,
    audioInterruptionPolicy: String,
    pauseOnDisconnect: Boolean,
    resumeOnReconnect: Boolean,
    onAudioFocusEnabledChange: (Boolean) -> Unit,
    onAudioInterruptionPolicyChange: (String) -> Unit,
    onPauseOnDisconnect: (Boolean) -> Unit,
    onResumeOnReconnect: (Boolean) -> Unit,
) {
    SettingsSectionCard(title = stringResource(R.string.audio_behavior_title)) {
        SettingsSwitchRow(
            title = stringResource(R.string.audio_focus_title),
            detail = stringResource(R.string.audio_focus_detail),
            checked = audioFocusEnabled,
            onCheckedChange = onAudioFocusEnabledChange,
        )
        SettingsChoiceGroupRow(
            title = stringResource(R.string.listening_interruption),
            detail = stringResource(if (audioFocusEnabled) R.string.listening_interruption_detail
                else R.string.audio_focus_disabled_detail),
            options = listOf(
                SettingsChoiceOption("duck", stringResource(R.string.listening_duck)),
                SettingsChoiceOption("pause", stringResource(R.string.listening_pause)),
            ),
            selectedValue = audioInterruptionPolicy,
            onOptionSelected = onAudioInterruptionPolicyChange,
            enabled = audioFocusEnabled,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_pause_on_disconnect),
            detail = stringResource(R.string.settings_pause_on_disconnect_detail),
            checked = pauseOnDisconnect,
            onCheckedChange = onPauseOnDisconnect,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_resume_on_reconnect),
            detail = stringResource(R.string.settings_resume_on_reconnect_detail),
            checked = resumeOnReconnect,
            onCheckedChange = onResumeOnReconnect,
            enabled = pauseOnDisconnect,
        )
    }
}
