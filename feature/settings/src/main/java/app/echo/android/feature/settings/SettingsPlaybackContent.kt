package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import kotlin.math.roundToInt

@Composable
internal fun SettingsPlaybackContent(
    status: EchoPlaybackStatus,
    playbackHapticsEnabled: Boolean,
    effectivePerformanceMode: String,
    showLyricsControlDeck: Boolean,
    onlineLyricsEnabled: Boolean,
    usbExclusiveEnabled: Boolean,
    usbBitPerfectEnabled: Boolean,
    trackTransitions: app.echo.android.model.playback.EchoTrackTransitionOptions,
    usbExclusiveAutoRequestOnStartup: Boolean,
    usbExclusiveTestResult: String,
    onPlaybackHapticsEnabledChange: (Boolean) -> Unit,
    onShowLyricsControlDeckChange: (Boolean) -> Unit,
    onOnlineLyricsEnabledChange: (Boolean) -> Unit,
    onUsbExclusiveEnabledChange: (Boolean) -> Unit,
    onUsbBitPerfectEnabledChange: (Boolean) -> Unit,
    onTrackTransitionsChange: (app.echo.android.model.playback.EchoTrackTransitionOptions) -> Unit,
    onUsbExclusiveAutoRequestOnStartupChange: (Boolean) -> Unit,
    onTestUsbExclusiveDriver: () -> Unit,
    notificationPermissionGranted: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {},
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
        SettingsActionRow(
            title = stringResource(R.string.settings_notification_permission),
            detail = if (notificationPermissionGranted) {
                stringResource(R.string.settings_notification_granted)
            } else {
                stringResource(R.string.settings_notification_denied)
            },
            actionLabel = if (notificationPermissionGranted) {
                stringResource(R.string.settings_on)
            } else {
                stringResource(R.string.settings_allow)
            },
            enabled = !notificationPermissionGranted,
            onClick = onRequestNotificationPermission,
        )
        SettingsActionRow(
            title = stringResource(R.string.settings_test_usb),
            detail = usbExclusiveTestDetail(status, usbExclusiveTestResult),
            enabled = status.diagnostics.usbConnected,
            actionLabel = stringResource(R.string.settings_test),
            onClick = onTestUsbExclusiveDriver,
        )
    }
}
