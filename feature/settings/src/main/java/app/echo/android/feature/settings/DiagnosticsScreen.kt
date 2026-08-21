package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoSectionTitle
import app.echo.android.design.PageChrome
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.OpraHeadphoneCorrectionState

@Composable
fun DiagnosticsScreen(
    status: EchoPlaybackStatus,
    equalizerState: EchoEqualizerState,
    opraState: OpraHeadphoneCorrectionState,
    onEqualizerEnabledChange: (Boolean) -> Unit,
    onEqualizerPresetSelected: (String) -> Unit,
    onEqualizerBandGainChange: (Int, Float) -> Unit,
    onEqualizerReset: () -> Unit,
    onOpraQueryChange: (String) -> Unit,
    onOpraSearch: () -> Unit,
    onOpraRefresh: () -> Unit,
    onOpraPresetSelected: (String) -> Unit,
    onOpraApplySelected: () -> Unit,
) {
    val diagnostics = status.diagnostics
    val bufferSeconds = "${diagnostics.bufferedMs / 1000}s"
    val codec = diagnostics.codec ?: "Media3"
    val lastCommand = commandLabel(diagnostics.lastCommand)
    val dspActive = diagnostics.offloadActive || equalizerState.active
    PageChrome(
        title = stringResource(R.string.diag_title),
        subtitle = stringResource(R.string.diag_subtitle),
        badge = playbackStateLabel(status.state),
        scrollable = true,
        scrollBottomPadding = 188.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SignalHeroCard(
                status = status,
                output = diagnostics.outputRoute,
                codec = codec,
                buffer = bufferSeconds,
                lastCommand = lastCommand,
            )
            SignalFlowPanel(
                codec = codec,
                output = diagnostics.outputRoute,
                dspActive = dspActive,
                diagnostics = diagnostics,
            )
            AudioFormatPanel(
                status = status,
                equalizerState = equalizerState,
            )
            EqualizerPanel(
                state = equalizerState,
                opraState = opraState,
                onEnabledChange = onEqualizerEnabledChange,
                onPresetSelected = onEqualizerPresetSelected,
                onBandGainChange = onEqualizerBandGainChange,
                onReset = onEqualizerReset,
                onOpraQueryChange = onOpraQueryChange,
                onOpraSearch = onOpraSearch,
                onOpraRefresh = onOpraRefresh,
                onOpraPresetSelected = onOpraPresetSelected,
                onOpraApplySelected = onOpraApplySelected,
            )
            UsbOutputPanel(status = status)
            CurrentStreamPanel(
                status = status,
                lastCommand = lastCommand,
                requestToken = diagnostics.requestToken,
            )
            HealthPanel(status = status)
        }
    }
}

@Composable
private fun UsbOutputPanel(status: EchoPlaybackStatus) {
    val diagnostics = status.diagnostics
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(signalPanelColor(0.64f))
            .border(signalPanelBorder(0.84f), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EchoSectionTitle(
                stringResource(R.string.diag_usb_exclusive),
                if (diagnostics.usbConnected) stringResource(R.string.diag_connected) else stringResource(R.string.diag_disconnected),
            )
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
}

@Composable
private fun UsbOutputLine(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun formatUsbSampleRates(sampleRates: List<Int>): String =
    if (sampleRates.isEmpty()) {
        stringResource(R.string.diag_unreported)
    } else {
        sampleRates.take(6).joinToString(" / ") { formatUsbSampleRate(it) } +
            if (sampleRates.size > 6) " ..." else ""
    }

private fun formatUsbSampleRate(sampleRateHz: Int): String =
    if (sampleRateHz % 1000 == 0) {
        "${sampleRateHz / 1000} kHz"
    } else {
        "${sampleRateHz / 1000f} kHz"
    }

