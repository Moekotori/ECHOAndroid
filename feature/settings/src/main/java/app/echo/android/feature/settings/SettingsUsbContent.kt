package app.echo.android.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoExpand
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoUsbOutputOptions

@Composable
fun UsbAudioSettingsContent(
    compactMode: Boolean,
    status: EchoPlaybackStatus,
    usbExclusiveEnabled: Boolean,
    usbBitPerfectEnabled: Boolean,
    options: EchoUsbOutputOptions,
    onOptionsChange: (EchoUsbOutputOptions) -> Unit,
    usbExclusiveAutoRequestOnStartup: Boolean,
    usbExclusiveTestResult: String,
    onUsbExclusiveEnabledChange: (Boolean) -> Unit,
    onUsbBitPerfectEnabledChange: (Boolean) -> Unit,
    onUsbExclusiveAutoRequestOnStartupChange: (Boolean) -> Unit,
    onTestUsbExclusiveDriver: () -> Unit,
    onOpenSound: () -> Unit,
) {
    CompositionLocalProvider(LocalSettingsCompactMode provides compactMode) {
        MaterialTheme(shapes = SettingsShapes) {
            val output = status.usbOutputPresentation()
            var showDetails by rememberSaveable { mutableStateOf(false) }
            var showTestResult by rememberSaveable { mutableStateOf(false) }
            UsbOutputDevicePanel(output)
            UsbSettingsSection(title = stringResource(R.string.usb_page_output_mode)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_usb_exclusive),
                    detail = stringResource(R.string.usb_page_exclusive_note),
                    checked = usbExclusiveEnabled,
                    onCheckedChange = onUsbExclusiveEnabledChange,
                )
                UsbOutputModeControl(usbBitPerfectEnabled, onUsbBitPerfectEnabledChange)
            }
            UsbSettingsSection(stringResource(R.string.usb_param_volume)) {
                val volumeLocked = status.diagnostics.bitPerfectState.locksUsbVolume(usbBitPerfectEnabled)
                UsbVolumeStatus(options, status.diagnostics, volumeLocked, onOptionsChange)
                UsbFineVolumeControl(options, volumeLocked, onOptionsChange)
            }
            UsbSettingsSection(title = stringResource(R.string.usb_page_connection_preferences)) {
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
            }
            UsbSettingsSection(title = stringResource(R.string.usb_page_checks)) {
                SettingsActionRow(
                    title = stringResource(R.string.settings_test_usb),
                    detail = stringResource(when {
                        !output.connected -> R.string.settings_usb_not_detected
                        !output.permissionGranted -> R.string.settings_usb_need_permission
                        !output.canTest -> R.string.usb_page_test_pause
                        else -> R.string.usb_page_test_note
                    }),
                    enabled = output.canTest,
                    actionLabel = stringResource(R.string.settings_test),
                    onClick = {
                        showTestResult = true
                        onTestUsbExclusiveDriver()
                    },
                )
                if (showTestResult) UsbDriverTestFeedback(usbExclusiveTestResult)
                SettingsActionRow(
                    title = stringResource(R.string.settings_quick_sound),
                    detail = stringResource(if (usbBitPerfectEnabled) R.string.path_bypass_settings_note
                        else R.string.usb_page_sound_note),
                    onClick = onOpenSound,
                )
                UsbAdvancedControls(options, status.diagnostics, output.bufferDurationMs, onOptionsChange)
                SettingsDisclosureRow(
                    title = stringResource(if (showDetails) R.string.usb_page_hide_details else R.string.usb_page_show_details),
                    detail = stringResource(R.string.usb_page_details_note),
                    expanded = showDetails,
                    onExpandedChange = { showDetails = it },
                )
                EchoExpand(showDetails) {
                    UsbSettingsSection(stringResource(R.string.usb_page_device_details), contentSpacing = 6.dp) {
                        UsbOutputDetails(status.diagnostics, output)
                        UsbConversionReadout(status.diagnostics)
                    }
                }
            }
        }
    }
}
