package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.EchoPlaybackStatus

@Composable
internal fun SettingsUsbContent(
    status: EchoPlaybackStatus,
    usbExclusiveEnabled: Boolean,
    usbBitPerfectEnabled: Boolean,
    usbExclusiveAutoRequestOnStartup: Boolean,
    usbExclusiveTestResult: String,
    onUsbExclusiveEnabledChange: (Boolean) -> Unit,
    onUsbBitPerfectEnabledChange: (Boolean) -> Unit,
    onUsbExclusiveAutoRequestOnStartupChange: (Boolean) -> Unit,
    onTestUsbExclusiveDriver: () -> Unit,
) {
    SettingsSectionCard(title = stringResource(R.string.settings_section_usb), secondary = true) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_usb_exclusive),
            detail = usbExclusiveDetail(status),
            checked = usbExclusiveEnabled,
            onCheckedChange = onUsbExclusiveEnabledChange,
        )
        if (status.diagnostics.usbConnected || usbExclusiveEnabled || usbBitPerfectEnabled ||
            usbExclusiveAutoRequestOnStartup) {
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
                title = stringResource(R.string.settings_test_usb),
                detail = usbExclusiveTestDetail(status, usbExclusiveTestResult),
                enabled = status.diagnostics.usbConnected,
                actionLabel = stringResource(R.string.settings_test),
                onClick = onTestUsbExclusiveDriver,
            )
        }
    }
}
