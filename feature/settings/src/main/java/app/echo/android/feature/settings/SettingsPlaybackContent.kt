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
    showLyricsControlDeck: Boolean,
    onlineLyricsEnabled: Boolean,
    lockScreenLyricsEnabled: Boolean,
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
    onShowLyricsControlDeckChange: (Boolean) -> Unit,
    onOnlineLyricsEnabledChange: (Boolean) -> Unit,
    onLockScreenLyricsEnabledChange: (Boolean) -> Unit,
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
    onPinQueueOffline: () -> Unit = {},
) {
    SettingsSectionCard(
        title = stringResource(R.string.settings_section_playback),
    ) {
        SettingsInfoRow(
            title = stringResource(R.string.settings_gapless),
            detail = stringResource(R.string.settings_gapless_detail),
        )
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
        SettingsSwitchRow(
            title = stringResource(R.string.settings_lyrics_sync_tools),
            detail = stringResource(R.string.settings_lyrics_sync_tools_detail),
            checked = showLyricsControlDeck,
            onCheckedChange = onShowLyricsControlDeckChange,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_playback_haptics),
            detail = stringResource(R.string.settings_playback_haptics_detail),
            checked = playbackHapticsEnabled,
            onCheckedChange = onPlaybackHapticsEnabledChange,
        )
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
        SettingsSwitchRow(
            title = stringResource(R.string.settings_usb_exclusive),
            detail = usbExclusiveDetail(status),
            checked = usbExclusiveEnabled,
            onCheckedChange = onUsbExclusiveEnabledChange,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_usb_bitperfect),
            detail = stringResource(R.string.settings_usb_bitperfect_detail),
            checked = usbBitPerfectEnabled,
            onCheckedChange = onUsbBitPerfectEnabledChange,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_usb_auto_request),
            detail = if (usbExclusiveAutoRequestOnStartup) {
                stringResource(R.string.settings_usb_auto_request_on)
            } else {
                stringResource(R.string.settings_usb_auto_request_off)
            },
            checked = usbExclusiveAutoRequestOnStartup,
            onCheckedChange = onUsbExclusiveAutoRequestOnStartupChange,
        )
        val notificationRuntimePermission =
            LocalEchoPlatformCapabilities.current.notificationRuntimePermission
        SettingsActionRow(
            title = stringResource(R.string.settings_notification_permission),
            detail = when {
                !notificationRuntimePermission ->
                    stringResource(R.string.settings_notification_not_required)
                notificationPermissionGranted ->
                    stringResource(R.string.settings_notification_granted)
                else -> stringResource(R.string.settings_notification_denied)
            },
            actionLabel = if (!notificationRuntimePermission || notificationPermissionGranted) {
                stringResource(R.string.settings_on)
            } else {
                stringResource(R.string.settings_allow)
            },
            disabledLabel = if (!notificationRuntimePermission) {
                stringResource(R.string.settings_on)
            } else {
                null
            },
            enabled = notificationRuntimePermission && !notificationPermissionGranted,
            onClick = onRequestNotificationPermission,
        )
        SettingsActionRow(
            title = stringResource(R.string.settings_test_usb),
            detail = usbExclusiveTestDetail(status, usbExclusiveTestResult),
            enabled = status.diagnostics.usbConnected,
            actionLabel = stringResource(R.string.settings_test),
            onClick = onTestUsbExclusiveDriver,
        )
        SettingsActionRow(
            title = stringResource(R.string.settings_pin_queue_offline),
            detail = stringResource(R.string.settings_pin_queue_offline_detail),
            onClick = onPinQueueOffline,
        )
    }
}
