package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.EchoAudioErrorKind
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.model.playback.EchoUsbOutputOptions

internal fun canRestoreUsbAdaptive(d: EchoPlaybackDiagnostics, strict: Boolean, options: EchoUsbOutputOptions): Boolean {
    if (strict || !d.usbExclusiveEnabled || !d.usbConnected || !d.usbHostPermissionGranted || d.usbHostPermissionPending) return false
    if (options.sampleRateHz == 0 && options.bitDepth == 0 && options.channelCount == 0) return false
    fun unsupported(value: Int, supported: List<Int>) = value > 0 && supported.isNotEmpty() && value !in supported
    return d.usbLastRequestError?.kind == EchoAudioErrorKind.UnsupportedFormat ||
        unsupported(options.sampleRateHz, d.usbSupportedSampleRates) ||
        unsupported(options.bitDepth, d.usbSupportedBitDepths) ||
        unsupported(options.channelCount, d.usbSupportedChannelCounts)
}

internal fun EchoUsbOutputOptions.withAdaptiveFormat() = copy(sampleRateHz = 0, bitDepth = 0, channelCount = 0)

@Composable
internal fun UsbFormatRecovery(d: EchoPlaybackDiagnostics, strict: Boolean, options: EchoUsbOutputOptions,
    onChange: (EchoUsbOutputOptions) -> Unit) {
    if (!canRestoreUsbAdaptive(d, strict, options)) return
    Column {
        SignalNote(stringResource(R.string.usb_format_recovery_note), error = true)
        TextButton(onClick = { onChange(options.withAdaptiveFormat()) }) {
            Text(stringResource(R.string.usb_format_recovery_action))
        }
    }
}
