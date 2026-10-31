package app.echo.android.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.model.playback.EchoUsbOutputOptions

@Composable
internal fun UsbOutputFormatControls(options: EchoUsbOutputOptions, d: EchoPlaybackDiagnostics,
    strict: Boolean, onChange: (EchoUsbOutputOptions) -> Unit) {
    val auto = stringResource(R.string.usb_param_auto)
    val rates = remember(d.usbConnected, d.usbSupportedSampleRates) {
        EchoUsbOutputOptions.SAMPLE_RATES.filter { !d.usbConnected || d.usbSupportedSampleRates.isEmpty() ||
            it in d.usbSupportedSampleRates }
    }
    val depths = remember(d.usbConnected, d.usbSupportedBitDepths) {
        listOf(16, 24, 32).filter { !d.usbConnected || d.usbSupportedBitDepths.isEmpty() || it in d.usbSupportedBitDepths }
    }
    val mono = stringResource(R.string.usb_param_mono)
    val stereo = stringResource(R.string.usb_param_stereo)
    val channels = listOf(1, 2).filter { !d.usbConnected || d.usbSupportedChannelCounts.isEmpty() ||
        it in d.usbSupportedChannelCounts }
    UsbSettingsSection(stringResource(R.string.usb_param_format)) {
        if (strict) Text(stringResource(R.string.usb_param_strict_locked),
            Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        UsbOutputParameterPicker(stringResource(R.string.diag_sample_rate),
            if (strict) auto else options.sampleRateHz.takeIf { it > 0 }?.let(::formatSampleRate) ?: auto,
            options.sampleRateHz.toString(), listOf(UsbParameterChoice("0", auto)) +
                rates.map { UsbParameterChoice(it.toString(), formatSampleRate(it)) }, !strict) {
            onChange(options.copy(sampleRateHz = it.toInt()))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        UsbOutputParameterPicker(stringResource(R.string.path_output_bits),
            if (strict) auto else options.bitDepth.takeIf { it > 0 }?.let { "$it bit" } ?: auto,
            options.bitDepth.toString(), listOf(UsbParameterChoice("0", auto)) +
                depths.map { UsbParameterChoice(it.toString(), "$it bit") }, !strict) {
            onChange(options.copy(bitDepth = it.toInt()))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        UsbOutputParameterPicker(stringResource(R.string.diag_channels),
            if (strict || options.channelCount == 0) auto else if (options.channelCount == 1) mono else stereo,
            options.channelCount.toString(), listOf(UsbParameterChoice("0", auto)) +
                channels.map { UsbParameterChoice(it.toString(), if (it == 1) mono else stereo) }, !strict) {
            onChange(options.copy(channelCount = it.toInt()))
        }
        Text(stringResource(if (d.usbConnected) R.string.usb_param_capabilities_note else R.string.usb_param_saved_note),
            Modifier.padding(top = 8.dp, bottom = 12.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    UsbSettingsSection(stringResource(R.string.usb_param_volume)) {
        UsbVolumeStatus(options, d, strict, onChange)

        UsbFineVolumeControl(options, strict, onChange)

    }
}
