package app.echo.android.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoUsbBufferMode
import app.echo.android.model.playback.EchoUsbOutputOptions

@Composable
internal fun SettingsUsbContent(
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
    pauseOnDisconnect: Boolean,
    resumeOnReconnect: Boolean,
    onPauseOnDisconnect: (Boolean) -> Unit,
    onResumeOnReconnect: (Boolean) -> Unit,
) {
    val output = status.usbOutputPresentation()
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showTestResult by rememberSaveable { mutableStateOf(false) }
    UsbOutputDevicePanel(output)
    UsbFormatRecovery(status.diagnostics, usbBitPerfectEnabled, options, onOptionsChange)
    UsbSettingsSection(title = stringResource(R.string.usb_page_output_mode), contentSpacing = 8.dp) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_usb_exclusive),
            detail = stringResource(R.string.usb_page_exclusive_note),
            checked = usbExclusiveEnabled,
            onCheckedChange = onUsbExclusiveEnabledChange,
        )
        UsbOutputModeControl(usbBitPerfectEnabled, onUsbBitPerfectEnabledChange)
    }
    UsbDopControls(options, status.diagnostics, onOptionsChange)
    UsbOutputFormatControls(options, status.diagnostics, usbBitPerfectEnabled, onOptionsChange)
    UsbConversionReadout(status.diagnostics)
    UsbSettingsSection(stringResource(R.string.usb_param_transport), secondary = true) {
        val bufferLabel = stringResource(when (options.bufferMode) {
            EchoUsbBufferMode.Responsive -> R.string.usb_buffer_responsive
            EchoUsbBufferMode.Balanced -> R.string.usb_buffer_balanced
            EchoUsbBufferMode.Stable -> R.string.usb_buffer_stable
        })
        UsbOutputParameterPicker(stringResource(R.string.usb_param_buffer), bufferLabel, options.bufferMode.id,
            listOf(UsbParameterChoice("responsive", stringResource(R.string.usb_buffer_responsive)),
                UsbParameterChoice("balanced", stringResource(R.string.usb_buffer_balanced)),
                UsbParameterChoice("stable", stringResource(R.string.usb_buffer_stable)))) {
            onOptionsChange(options.copy(bufferMode = EchoUsbBufferMode.fromId(it)))
        }
        Text(stringResource(R.string.usb_param_buffer_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        SettingsSwitchRow(stringResource(R.string.usb_param_keep_alive),
            stringResource(R.string.usb_param_keep_alive_note), options.keepAliveOnPause,
            onCheckedChange = { onOptionsChange(options.copy(keepAliveOnPause = it)) })
    }
    UsbSettingsSection(title = stringResource(R.string.usb_page_connection_preferences), secondary = true) {
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
        SettingsSwitchRow(stringResource(R.string.settings_pause_on_disconnect),
            stringResource(R.string.settings_pause_on_disconnect_detail), pauseOnDisconnect,
            onCheckedChange = onPauseOnDisconnect)
        SettingsSwitchRow(stringResource(R.string.settings_resume_on_reconnect),
            stringResource(R.string.settings_resume_on_reconnect_detail), resumeOnReconnect,
            onCheckedChange = onResumeOnReconnect, enabled = pauseOnDisconnect)
    }
    UsbSettingsSection(title = stringResource(R.string.usb_page_checks), secondary = true) {
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
            onClick = { showTestResult = true; onTestUsbExclusiveDriver() },
        )
        if (showTestResult) UsbDriverTestFeedback(usbExclusiveTestResult)
        SettingsActionRow(
            title = stringResource(if (showDetails) R.string.usb_page_hide_details else R.string.usb_page_show_details),
            detail = stringResource(R.string.usb_page_details_note),
            onClick = { showDetails = !showDetails },
        )
        SettingsActionRow(
            title = stringResource(R.string.settings_quick_sound),
            detail = stringResource(if (usbBitPerfectEnabled) R.string.path_bypass_settings_note
                else R.string.usb_page_sound_note),
            onClick = onOpenSound,
        )
        UsbOutputResetControls(options, onOptionsChange)
    }
    if (showDetails) {
        UsbSettingsSection(stringResource(R.string.usb_page_device_details), secondary = true, contentSpacing = 16.dp) {
            UsbOutputDetails(status.diagnostics, output)
        }
    }
}
