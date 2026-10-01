package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.design.LocalEchoPlatformCapabilities
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoReplayGainMode
import app.echo.android.model.playback.EchoReplayGainPreampMaxDb
import app.echo.android.model.playback.EchoReplayGainPreampMinDb
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import kotlin.math.roundToInt

@Composable
internal fun SettingsPlaybackContent(
    status: EchoPlaybackStatus,
    playbackHapticsEnabled: Boolean,
    effectivePerformanceMode: String,
    onlineLyricsEnabled: Boolean,
    lockScreenLyricsEnabled: Boolean,
    floatingLyrics: app.echo.android.model.settings.EchoFloatingLyricsSettings,
    floatingLyricsPermissionGranted: Boolean,
    usbExclusiveEnabled: Boolean,
    usbBitPerfectEnabled: Boolean,
    trackTransitions: app.echo.android.model.playback.EchoTrackTransitionOptions,
    usbExclusiveAutoRequestOnStartup: Boolean,
    pauseOnAudioDisconnect: Boolean,
    resumeOnAudioReconnect: Boolean,
    replayGainEnabled: Boolean,
    replayGainMode: String,
    replayGainPreampDb: Float,
    usbExclusiveTestResult: String,
    onPlaybackHapticsEnabledChange: (Boolean) -> Unit,
    onOnlineLyricsEnabledChange: (Boolean) -> Unit,
    onLockScreenLyricsEnabledChange: (Boolean) -> Unit,
    onFloatingLyricsChange: (app.echo.android.model.settings.EchoFloatingLyricsSettings) -> Unit,
    onRequestFloatingLyricsPermission: () -> Unit,
    onUsbExclusiveEnabledChange: (Boolean) -> Unit,
    onUsbBitPerfectEnabledChange: (Boolean) -> Unit,
    onTrackTransitionsChange: (app.echo.android.model.playback.EchoTrackTransitionOptions) -> Unit,
    onUsbExclusiveAutoRequestOnStartupChange: (Boolean) -> Unit,
    onPauseOnAudioDisconnectChange: (Boolean) -> Unit,
    onResumeOnAudioReconnectChange: (Boolean) -> Unit,
    onReplayGainChange: (Boolean, Float) -> Unit,
    onReplayGainModeChange: (EchoReplayGainMode) -> Unit,
    onTestUsbExclusiveDriver: () -> Unit,
    notificationPermissionGranted: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {},
) {
    SettingsSectionCard(title = stringResource(R.string.settings_section_listening)) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_pause_on_disconnect),
            detail = stringResource(R.string.settings_pause_on_disconnect_detail),
            checked = pauseOnAudioDisconnect,
            onCheckedChange = onPauseOnAudioDisconnectChange,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_resume_on_reconnect),
            detail = stringResource(R.string.settings_resume_on_reconnect_detail),
            checked = resumeOnAudioReconnect,
            onCheckedChange = onResumeOnAudioReconnectChange,
            enabled = pauseOnAudioDisconnect,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_playback_haptics),
            detail = stringResource(R.string.settings_playback_haptics_detail),
            checked = playbackHapticsEnabled,
            onCheckedChange = onPlaybackHapticsEnabledChange,
        )
    }
    SettingsSectionCard(title = stringResource(R.string.settings_section_lyrics)) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_online_lyrics),
            detail = stringResource(R.string.settings_online_lyrics_detail),
            checked = onlineLyricsEnabled,
            onCheckedChange = onOnlineLyricsEnabledChange,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_lock_lyrics),
            detail = stringResource(R.string.settings_lock_lyrics_detail),
            checked = lockScreenLyricsEnabled,
            onCheckedChange = onLockScreenLyricsEnabledChange,
        )
        SettingsFloatingLyricsRows(
            settings = floatingLyrics,
            permissionGranted = floatingLyricsPermissionGranted,
            onChange = onFloatingLyricsChange,
            onRequestPermission = onRequestFloatingLyricsPermission,
        )
        val notificationRuntimePermission =
            LocalEchoPlatformCapabilities.current.notificationRuntimePermission
        if (notificationRuntimePermission && !notificationPermissionGranted) {
            SettingsActionRow(
                title = stringResource(R.string.settings_notification_permission),
                detail = stringResource(R.string.settings_notification_denied),
                actionLabel = stringResource(R.string.settings_allow),
                onClick = onRequestNotificationPermission,
            )
        }
    }
    SettingsSectionCard(title = stringResource(R.string.settings_section_transition), secondary = true) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_track_fade),
            detail = stringResource(if (usbBitPerfectEnabled) R.string.settings_track_fade_bypass else R.string.settings_track_fade_detail),
            checked = trackTransitions.fadeEnabled,
            onCheckedChange = { onTrackTransitionsChange(trackTransitions.copy(fadeEnabled = it)) },
        )
        if (trackTransitions.fadeEnabled) SettingsSliderRow(
            title = stringResource(R.string.settings_track_fade_duration),
            valueLabel = { stringResource(R.string.settings_track_fade_seconds, it) },
            value = trackTransitions.fadeDurationMs / 1000f,
            valueRange = 0.5f..5f,
            steps = 8,
            onValueChange = {
                onTrackTransitionsChange(
                    trackTransitions.copy(fadeDurationMs = (it * 1000f).roundToInt()),
                )
            },
        )
        val smartBypassed = usbExclusiveEnabled || usbBitPerfectEnabled ||
            effectivePerformanceMode == EchoEffectivePerformanceMode.Lightweight.id
        SettingsSwitchRow(
            title = stringResource(R.string.settings_smart_transition),
            detail = stringResource(
                if (smartBypassed) R.string.settings_smart_transition_bypass
                else R.string.settings_smart_transition_detail,
            ),
            checked = trackTransitions.smartEnabled,
            onCheckedChange = { onTrackTransitionsChange(trackTransitions.copy(smartEnabled = it)) },
        )
    }
    SettingsSectionCard(title = stringResource(R.string.settings_section_volume), secondary = true) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_replay_gain),
            detail = stringResource(
                if (usbBitPerfectEnabled) R.string.settings_replay_gain_bypass
                else R.string.settings_replay_gain_detail,
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
    }
    SettingsUsbContent(
        status, usbExclusiveEnabled, usbBitPerfectEnabled, usbExclusiveAutoRequestOnStartup,
        usbExclusiveTestResult, onUsbExclusiveEnabledChange, onUsbBitPerfectEnabledChange,
        onUsbExclusiveAutoRequestOnStartupChange, onTestUsbExclusiveDriver,
    )
}
