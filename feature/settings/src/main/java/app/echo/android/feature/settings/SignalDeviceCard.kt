package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.EchoOutputDeviceKind
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.dsdFamilyLabel
import app.echo.android.model.playback.isDsdSource

@Composable
internal fun SignalDeviceCard(status: EchoPlaybackStatus) {
    val d = status.diagnostics
    val kind = if (d.usbExclusiveStreaming) EchoOutputDeviceKind.Usb else EchoOutputDeviceKind.fromId(d.outputDeviceKind)
    val deviceName = (if (d.usbExclusiveStreaming) d.usbDeviceName else d.outputDeviceName)
        ?.takeIf { it.isNotBlank() }
    SignalSection(stringResource(R.string.diag_output_end), deviceName) {
        SignalReadout(stringResource(R.string.diag_output_route), outputDeviceKindLabel(kind.id))
        SignalNote(stringResource(R.string.path_source_format))
        SignalReadout(
            stringResource(R.string.diag_sample_rate),
            if (d.isDsdSource()) d.dsdFamilyLabel() ?: "—"
            else (d.sampleRateHz ?: status.track?.sampleRateHz)?.takeIf { it > 0 }?.let(::formatSampleRate) ?: "—",
        )
        SignalReadout(stringResource(R.string.diag_bit_depth), d.bitDepth?.takeIf { it > 0 }?.let { "$it bit" } ?: "—")
        SignalReadout(stringResource(R.string.diag_channels), d.channelCount?.takeIf { it > 0 }?.let(::formatChannels) ?: "—")
    }
}
