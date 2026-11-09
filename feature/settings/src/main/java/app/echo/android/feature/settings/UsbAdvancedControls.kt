package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.echo.android.design.EchoExpand
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.model.playback.EchoUsbBufferMode
import app.echo.android.model.playback.EchoUsbFineVolume
import app.echo.android.model.playback.EchoUsbOutputOptions

@Composable
internal fun UsbAdvancedControls(
    options: EchoUsbOutputOptions,
    diagnostics: EchoPlaybackDiagnostics,
    bufferDurationMs: Long?,
    onOptionsChange: (EchoUsbOutputOptions) -> Unit,
) {
    var showTroubleshooting by rememberSaveable { mutableStateOf(false) }
    SettingsDisclosureRow(
        title = stringResource(R.string.usb_troubleshooting),
        detail = stringResource(R.string.usb_troubleshooting_note),
        expanded = showTroubleshooting,
        onExpandedChange = { showTroubleshooting = it },
    )
    EchoExpand(showTroubleshooting) {
        Column {
            UsbSettingsSection(stringResource(R.string.usb_param_buffer)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.usb_buffer_stable),
                    detail = stringResource(R.string.usb_param_buffer_note),
                    checked = options.bufferMode == EchoUsbBufferMode.Stable,
                    onCheckedChange = { stable ->
                        val mode = if (stable) EchoUsbBufferMode.Stable else EchoUsbBufferMode.Balanced
                        onOptionsChange(options.copy(bufferMode = mode))
                    },
                )
                bufferDurationMs?.let { SignalNote(stringResource(R.string.usb_param_buffer_capacity, it)) }
            }
            UsbOutputResetControls(options, onOptionsChange)
        }
    }

    val deviceKey = if (diagnostics.usbConnected) {
        EchoUsbFineVolume.deviceKey(diagnostics.usbVendorId, diagnostics.usbProductId)
    } else null
    val compatibilityTitle = stringResource(R.string.usb_dac_compatibility)
    if (deviceKey == null) {
        SettingsInfoRow(compatibilityTitle, stringResource(R.string.usb_dac_compatibility_connect))
        return
    }
    var showCompatibility by rememberSaveable(deviceKey) { mutableStateOf(false) }
    SettingsDisclosureRow(
        title = compatibilityTitle,
        detail = diagnostics.usbDeviceName ?: stringResource(R.string.usb_dac_compatibility_note),
        expanded = showCompatibility,
        onExpandedChange = { showCompatibility = it },
    )
    key(deviceKey) {
        EchoExpand(showCompatibility) {
            Column {
                SettingsSwitchRow(
                    title = stringResource(R.string.usb_param_keep_alive),
                    detail = stringResource(R.string.usb_param_keep_alive_note),
                    checked = options.keepAliveByDevice[deviceKey] ?: options.keepAliveOnPause,
                    onCheckedChange = { enabled ->
                        val overrides = (options.keepAliveByDevice - deviceKey) + (deviceKey to enabled)
                        onOptionsChange(options.copy(keepAliveByDevice = overrides))
                    },
                )
                UsbDopControls(options, deviceKey, onOptionsChange)
            }
        }
    }
}
