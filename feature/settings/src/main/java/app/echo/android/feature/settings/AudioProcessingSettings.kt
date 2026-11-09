package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Column
import app.echo.android.design.EchoExpand
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.EchoReplayGainMode
import app.echo.android.model.playback.EchoReplayGainPreampMaxDb
import app.echo.android.model.playback.EchoReplayGainPreampMinDb
import app.echo.android.model.playback.EchoTrackTransitionOptions
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import kotlin.math.roundToInt

/** Processing preferences remain saved while their current output bypasses them. */
@Composable
internal fun AudioReplayGainSettings(
    enabled: Boolean,
    mode: String,
    preampDb: Float,
    bitPerfect: Boolean,
    onChange: (Boolean, Float) -> Unit,
    onModeChange: (EchoReplayGainMode) -> Unit,
) {
    SettingsSectionCard(title = stringResource(R.string.settings_section_volume)) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_replay_gain),
            detail = stringResource(if (bitPerfect) R.string.settings_replay_gain_bypass else R.string.audio_replay_gain_detail),
            checked = enabled,
            onCheckedChange = { onChange(it, preampDb) },
            enabled = !bitPerfect,
        )
        EchoExpand(enabled) { Column {
            SettingsChoiceGroupRow(
                title = stringResource(R.string.settings_replay_gain_mode),
                detail = stringResource(R.string.settings_replay_gain_mode_detail),
                options = listOf(
                    SettingsChoiceOption(EchoReplayGainMode.Auto.id, stringResource(R.string.dsp_auto)),
                    SettingsChoiceOption(EchoReplayGainMode.Track.id, stringResource(R.string.dsp_track)),
                    SettingsChoiceOption(EchoReplayGainMode.Album.id, stringResource(R.string.dsp_album)),
                ),
                selectedValue = EchoReplayGainMode.fromId(mode).id,
                onOptionSelected = { onModeChange(EchoReplayGainMode.fromId(it)) },
                enabled = !bitPerfect,
            )
            SettingsSliderRow(
                title = stringResource(R.string.eq_preamp),
                valueLabel = { formatEqGain(it) },
                value = preampDb.coerceIn(EchoReplayGainPreampMinDb, EchoReplayGainPreampMaxDb),
                valueRange = EchoReplayGainPreampMinDb..EchoReplayGainPreampMaxDb,
                steps = 35,
                onValueChange = { onChange(true, it) },
                enabled = !bitPerfect,
                neutralValue = 0f,
            )
        } }
    }
}

@Composable
internal fun AudioTransitionSettings(
    options: EchoTrackTransitionOptions,
    usbExclusive: Boolean,
    bitPerfect: Boolean,
    performanceMode: String,
    onChange: (EchoTrackTransitionOptions) -> Unit,
) {
    SettingsSectionCard(title = stringResource(R.string.settings_section_transition)) {
        SettingsInfoRow(title = stringResource(R.string.settings_gapless),
            detail = stringResource(R.string.settings_gapless_detail))
        AudioTransitionControls(options, usbExclusive, bitPerfect, performanceMode, onChange)
    }
}

@Composable
internal fun AudioTransitionControls(
    options: EchoTrackTransitionOptions,
    usbExclusive: Boolean,
    bitPerfect: Boolean,
    performanceMode: String,
    onChange: (EchoTrackTransitionOptions) -> Unit,
) {
    SettingsSwitchRow(
        title = stringResource(R.string.manual_fade_title),
        detail = stringResource(if (bitPerfect) R.string.settings_track_fade_bypass else R.string.manual_fade_detail),
        checked = options.playPauseFadeEnabled,
        onCheckedChange = { onChange(options.copy(playPauseFadeEnabled = it)) },
        enabled = !bitPerfect,
    )
    SettingsSwitchRow(
        title = stringResource(R.string.settings_track_fade),
        detail = stringResource(if (bitPerfect) R.string.settings_track_fade_bypass else R.string.settings_track_fade_detail),
        checked = options.fadeEnabled,
        onCheckedChange = { onChange(options.copy(fadeEnabled = it)) },
        enabled = !bitPerfect,
    )
    if (options.fadeEnabled) SettingsSliderRow(
        title = stringResource(R.string.settings_track_fade_duration),
        valueLabel = { stringResource(R.string.settings_track_fade_seconds, it) },
        value = options.fadeDurationMs / 1000f,
        valueRange = 0.5f..5f,
        steps = 8,
        onValueChange = { onChange(options.copy(fadeDurationMs = (it * 1000f).roundToInt())) },
        enabled = !bitPerfect,
    )
    val bypassed = usbExclusive || bitPerfect || performanceMode == EchoEffectivePerformanceMode.Lightweight.id
    SettingsSwitchRow(
        title = stringResource(R.string.settings_smart_transition),
        detail = stringResource(when {
            usbExclusive || bitPerfect -> R.string.audio_mix_usb_blocked
            bypassed -> R.string.audio_mix_lightweight_blocked
            else -> R.string.automix_description
        }),
        checked = options.smartEnabled,
        onCheckedChange = { onChange(options.copy(smartEnabled = it)) },
        enabled = !bypassed,
    )
}
