package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.formatDuration
import app.echo.android.model.playback.*
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun CurrentStreamPanel(status: EchoPlaybackStatus, positionFlow: StateFlow<PlaybackPositionState>) {
    // Collect high-frequency playback position only in this visible leaf.
    val position by positionFlow.collectAsState()
    SignalSection(stringResource(R.string.diag_current_stream), status.track?.album) {
        SignalReadout(stringResource(R.string.diag_track), status.track?.title ?: stringResource(R.string.diag_none))
        SignalReadout(stringResource(R.string.diag_artist), status.track?.artist ?: stringResource(R.string.diag_none))
        SignalReadout(stringResource(R.string.diag_progress), "${formatDuration(position.positionMs)} / ${formatDuration(position.durationMs.takeIf { it > 0 } ?: status.durationMs)}")
        SignalReadout(stringResource(R.string.diag_mode), "${repeatModeLabel(status.repeatMode)} · ${if (status.shuffleEnabled) stringResource(R.string.diag_shuffle_on) else stringResource(R.string.diag_order_play)}")
        SignalReadout(stringResource(R.string.diag_command), commandLabel(status.diagnostics.lastCommand))
        SignalReadout(stringResource(R.string.diag_token), status.diagnostics.requestToken.toString())
    }
}

@Composable
internal fun HealthPanel(status: EchoPlaybackStatus) {
    val d = status.diagnostics
    SignalSection(stringResource(R.string.diag_health), stringResource(R.string.diag_no_error).takeIf { d.lastError == null }) {
        SignalReadout(stringResource(R.string.diag_output_route), d.outputRoute)
        SignalReadout(stringResource(R.string.diag_buffer_remaining), "${d.bufferedMs / 1000}s")
        SignalReadout(stringResource(R.string.diag_decode_error), d.lastError?.message ?: stringResource(R.string.diag_none))
        SignalReadout(stringResource(R.string.diag_usb_fallback), d.usbLastRequestError?.message ?: stringResource(R.string.diag_none))
    }
}

@Composable
internal fun UsbOutputPanel(status: EchoPlaybackStatus) {
    val diagnostics = status.diagnostics
    SignalSection(
        title = stringResource(R.string.diag_usb_exclusive),
        subtitle = if (diagnostics.usbConnected) stringResource(R.string.diag_connected) else stringResource(R.string.diag_disconnected),
    ) {
        UsbOutputLine(stringResource(R.string.diag_device), diagnostics.usbDeviceName ?: stringResource(R.string.diag_no_usb))
        UsbOutputLine(
            stringResource(R.string.diag_path),
            when {
                diagnostics.usbExclusiveStreaming -> stringResource(
                    R.string.diag_usb_exclusive_stream,
                    diagnostics.usbExclusiveTransport ?: "pcm",
                )
                diagnostics.usbBitPerfectActive -> stringResource(R.string.diag_usb_bit_perfect)
                diagnostics.usbExclusiveEnabled && diagnostics.usbHostPermissionPending -> stringResource(R.string.diag_usb_wait_auth)
                diagnostics.usbExclusiveEnabled && diagnostics.usbHostPermissionGranted && diagnostics.usbAudioHasIsochronousOut -> stringResource(R.string.diag_usb_iso_pending)
                diagnostics.usbExclusiveEnabled && diagnostics.usbHostPermissionGranted -> stringResource(R.string.diag_usb_takeover_pending)
                diagnostics.usbExclusiveEnabled && diagnostics.usbConnected -> stringResource(R.string.diag_usb_unauthorized)
                diagnostics.usbHostPermissionGranted -> stringResource(R.string.diag_usb_host_granted)
                diagnostics.usbHostPermissionPending -> stringResource(R.string.diag_usb_wait_auth)
                diagnostics.usbBitPerfectSupported -> stringResource(R.string.diag_usb_bit_perfect_supported)
                diagnostics.usbConnected -> "Android mixer"
                else -> "Media3 / AudioTrack"
            },
        )
        UsbOutputLine(
            stringResource(R.string.diag_sample_rate),
            formatUsbSampleRates(diagnostics.usbSupportedSampleRates),
        )
        UsbOutputLine(
            stringResource(R.string.diag_request),
            diagnostics.usbLastRequestedSampleRateHz?.let(::formatUsbSampleRate) ?: stringResource(R.string.diag_not_requested),
        )
        if (diagnostics.usbConnected) {
            UsbOutputLine(
                stringResource(R.string.diag_usb_permission),
                when {
                    diagnostics.usbHostPermissionGranted -> stringResource(R.string.diag_authorized)
                    diagnostics.usbHostPermissionPending -> stringResource(R.string.diag_waiting_confirm)
                    diagnostics.usbExclusiveEnabled -> stringResource(R.string.diag_unauthorized)
                    else -> stringResource(R.string.diag_not_requested_short)
                },
            )
        }
        diagnostics.usbAudioClass?.let { UsbOutputLine("UAC", it) }
        if (diagnostics.usbAudioInterfaceCount > 0) {
            UsbOutputLine(
                stringResource(R.string.diag_interface),
                "${diagnostics.usbAudioInterfaceCount} audio / ${diagnostics.usbAudioStreamingInterfaceCount} stream",
            )
        }
        diagnostics.usbAudioEndpointSummary?.let { UsbOutputLine(stringResource(R.string.diag_endpoint), it) }
        if (diagnostics.usbAudioHasIsochronousOut || diagnostics.usbAudioHasFeedbackEndpoint) {
            UsbOutputLine(
                stringResource(R.string.diag_transport),
                when {
                    diagnostics.usbAudioHasIsochronousOut && diagnostics.usbAudioHasFeedbackEndpoint -> "iso OUT + feedback"
                    diagnostics.usbAudioHasIsochronousOut -> "iso OUT"
                    else -> "feedback"
                },
            )
        }
        diagnostics.usbAudioDescriptorError?.let { error ->
            UsbOutputLine("Descriptor", error)
        }
        diagnostics.usbLastRequestError?.let { error ->
            UsbOutputLine(stringResource(R.string.diag_fallback), error.message)
        }
    }
}

@Composable
private fun UsbOutputLine(label: String, value: String) = SignalReadout(label, value)

@Composable
private fun formatUsbSampleRates(sampleRates: List<Int>): String =
    if (sampleRates.isEmpty()) {
        stringResource(R.string.diag_unreported)
    } else {
        sampleRates.joinToString(" / ") { formatUsbSampleRate(it) }
    }

private fun formatUsbSampleRate(sampleRateHz: Int): String =
    if (sampleRateHz % 1000 == 0) {
        "${sampleRateHz / 1000} kHz"
    } else {
        "${sampleRateHz / 1000f} kHz"
    }

