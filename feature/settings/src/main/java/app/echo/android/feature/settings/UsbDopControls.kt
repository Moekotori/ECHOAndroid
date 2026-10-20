package app.echo.android.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.EchoDsdRates
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.model.playback.EchoUsbOutputOptions

@Composable
internal fun UsbDopControls(options: EchoUsbOutputOptions, diagnostics: EchoPlaybackDiagnostics,
    onChange: (EchoUsbOutputOptions) -> Unit) {
    val vendor = diagnostics.usbVendorId
    val product = diagnostics.usbProductId
    val key = if (diagnostics.usbConnected && vendor != null && product != null)
        EchoUsbOutputOptions.dopDeviceKey(vendor, product) else null
    val limit = key?.let { options.dopDeviceLimits[it] }
    UsbSettingsSection(stringResource(R.string.usb_dop_title), secondary = true) {
        SettingsSwitchRow(stringResource(R.string.usb_dop_enable),
            stringResource(if (key == null) R.string.usb_dop_connect else R.string.usb_dop_note),
            limit != null, enabled = key != null,
            onCheckedChange = { enabled ->
                if (key != null) onChange(options.copy(dopDeviceLimits = if (enabled)
                    options.dopDeviceLimits + (key to EchoDsdRates.Dsd64Hz) else options.dopDeviceLimits - key))
            })
        if (key != null && limit != null) {
            val choices = EchoUsbOutputOptions.DOP_DSD_RATES.map { rate ->
                UsbParameterChoice(rate.toString(), EchoDsdRates.familyLabel(rate)!!)
            }
            UsbOutputParameterPicker(stringResource(R.string.usb_dop_max),
                EchoDsdRates.familyLabel(limit) ?: "DSD64", limit.toString(), choices) { selected ->
                onChange(options.copy(dopDeviceLimits = options.dopDeviceLimits + (key to selected.toInt())))
            }
        }
        Text(stringResource(R.string.usb_dop_strict_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
