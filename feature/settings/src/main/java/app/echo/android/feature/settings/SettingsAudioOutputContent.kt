package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.EchoReplayGainMode
import app.echo.android.model.playback.EchoReplayGainPreampMaxDb
import app.echo.android.model.playback.EchoReplayGainPreampMinDb
import app.echo.android.model.playback.EchoTrackTransitionOptions

/** Audio output controls share one persisted policy and the existing service-owned DSP. */
@Composable
internal fun SettingsAudioOutputContent(
    audioFocusEnabled: Boolean,
    audioInterruptionPolicy: String,
    trackTransitions: EchoTrackTransitionOptions,
    usbBitPerfectEnabled: Boolean,
    replayGainEnabled: Boolean,
    replayGainMode: String,
    replayGainPreampDb: Float,
    onAudioFocusEnabledChange: (Boolean) -> Unit,
    onAudioInterruptionPolicyChange: (String) -> Unit,
    onTrackTransitionsChange: (EchoTrackTransitionOptions) -> Unit,
    onReplayGainChange: (Boolean, Float) -> Unit,
    onReplayGainModeChange: (EchoReplayGainMode) -> Unit,
    onResetAudioOutput: () -> Unit,
) {
    SettingsSectionCard(title = stringResource(R.string.audio_output_title)) {
        SettingsSwitchRow(
            title = stringResource(R.string.audio_focus_title),
            detail = stringResource(R.string.audio_focus_detail),
            checked = audioFocusEnabled,
            onCheckedChange = onAudioFocusEnabledChange,
        )
        if (audioFocusEnabled) {
            SettingsChoiceGroupRow(
                title = stringResource(R.string.listening_interruption),
                detail = stringResource(R.string.listening_interruption_detail),
                options = listOf(
                    SettingsChoiceOption("duck", stringResource(R.string.listening_duck)),
                    SettingsChoiceOption("pause", stringResource(R.string.listening_pause)),
                ),
                selectedValue = audioInterruptionPolicy,
                onOptionSelected = onAudioInterruptionPolicyChange,
            )
        } else {
            SettingsInfoRow(title = stringResource(R.string.listening_interruption),
                detail = stringResource(R.string.audio_focus_disabled_detail))
        }
        SettingsSwitchRow(
            title = stringResource(R.string.settings_replay_gain),
            detail = stringResource(
                if (usbBitPerfectEnabled) R.string.settings_replay_gain_bypass
                else R.string.audio_replay_gain_detail,
            ),
            checked = replayGainEnabled,
            onCheckedChange = { onReplayGainChange(it, replayGainPreampDb) },
        )
        if (replayGainEnabled) {
            SettingsChoiceGroupRow(
                title = stringResource(R.string.settings_replay_gain_mode),
                detail = stringResource(R.string.settings_replay_gain_mode_detail),
                options = listOf(
                    SettingsChoiceOption(EchoReplayGainMode.Auto.id, stringResource(R.string.dsp_auto)),
                    SettingsChoiceOption(EchoReplayGainMode.Track.id, stringResource(R.string.dsp_track)),
                    SettingsChoiceOption(EchoReplayGainMode.Album.id, stringResource(R.string.dsp_album)),
                ),
                selectedValue = EchoReplayGainMode.fromId(replayGainMode).id,
                onOptionSelected = { onReplayGainModeChange(EchoReplayGainMode.fromId(it)) },
            )
            SettingsSliderRow(
                title = stringResource(R.string.eq_preamp),
                valueLabel = { formatEqGain(it) },
                value = replayGainPreampDb.coerceIn(EchoReplayGainPreampMinDb, EchoReplayGainPreampMaxDb),
                valueRange = EchoReplayGainPreampMinDb..EchoReplayGainPreampMaxDb,
                steps = 18,
                onValueChange = { onReplayGainChange(true, it) },
            )
        }

        SettingsSwitchRow(
            title = stringResource(R.string.manual_fade_title),
            detail = stringResource(R.string.manual_fade_detail),
            checked = trackTransitions.playPauseFadeEnabled,
            onCheckedChange = { onTrackTransitionsChange(trackTransitions.copy(playPauseFadeEnabled = it)) },
        )
        SettingsActionRow(
            title = stringResource(R.string.audio_output_reset),
            detail = stringResource(R.string.audio_output_reset_detail),
            actionLabel = stringResource(R.string.audio_output_reset_action),
            onClick = onResetAudioOutput,
        )
    }
}
