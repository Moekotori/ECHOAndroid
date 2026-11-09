package app.echo.android.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.EchoDsdRates
import app.echo.android.model.playback.EchoUsbOutputOptions

@Composable
internal fun UsbDopControls(options: EchoUsbOutputOptions, deviceKey: String,
    onChange: (EchoUsbOutputOptions) -> Unit) {
    val limit = options.dopDeviceLimits[deviceKey]
    UsbSettingsSection(stringResource(R.string.usb_dop_title)) {
        SettingsSwitchRow(stringResource(R.string.usb_dop_enable),
            stringResource(R.string.usb_dop_note),
            limit != null,
            onCheckedChange = { enabled ->
                onChange(options.copy(dopDeviceLimits = if (enabled)
                    options.dopDeviceLimits + (deviceKey to EchoDsdRates.Dsd64Hz) else options.dopDeviceLimits - deviceKey))
            })
        if (limit != null) {
            val choices = EchoUsbOutputOptions.DOP_DSD_RATES.map { rate ->
                UsbParameterChoice(rate.toString(), EchoDsdRates.familyLabel(rate)!!)
            }
            UsbOutputParameterPicker(stringResource(R.string.usb_dop_max),
                EchoDsdRates.familyLabel(limit) ?: "DSD64", limit.toString(), choices) { selected ->
                onChange(options.copy(dopDeviceLimits = options.dopDeviceLimits + (deviceKey to selected.toInt())))
            }
        }
        Text(stringResource(R.string.usb_dop_strict_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
