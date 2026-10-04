package app.echo.android.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.*

internal data class SignalPathStage(
    val label: String,
    val value: String,
    val detail: String?,
    val icon: ImageVector,
    val highlighted: Boolean,
    val facts: List<Pair<String, String>> = emptyList(),
)

/** Present the existing diagnostics contract; do not infer a DAC format from track metadata. */
@Composable
internal fun signalPathStages(
    status: EchoPlaybackStatus,
    equalizer: EchoEqualizerState,
    balance: EchoChannelBalanceState,
    dsp: EchoDspSettings,
): List<SignalPathStage> {
    val d = status.diagnostics
    val live = status.isPlaying && status.state != EchoPlaybackState.Error
    val unknown = stringResource(R.string.diag_unreported)
    val sourceRate = d.sampleRateHz?.takeIf { it > 0 }
    // The mapper omits decodedSampleRateHz when the reported stream rate equals the source.
    // This is a stream-format readout, not a measurement of the final output clock.
    val streamRate = d.decodedSampleRateHz?.takeIf { it > 0 }
        ?: sourceRate?.takeUnless { d.isDsdSource() }
    val reported = !d.codec.isNullOrBlank() && streamRate != null
    val pcLibrary = EchoLinkPlaybackUri.isPcLibrarySource(
        sourceId = status.track?.sourceId, mediaId = status.track?.id, uri = status.track?.uri.orEmpty(),
    )
    val outputKind = if (d.usbExclusiveStreaming) EchoOutputDeviceKind.Usb else EchoOutputDeviceKind.fromId(d.outputDeviceKind)
    val outputName = (if (d.usbExclusiveStreaming) d.usbDeviceName else d.outputDeviceName)?.takeIf { it.isNotBlank() }
    return buildList {
        add(SignalPathStage(
            label = stringResource(R.string.diag_source_file),
            value = if (status.track == null) stringResource(R.string.diag_waiting_playback) else d.fileFormatLabel(),
            detail = status.track?.let { listOf(it.title, it.artist).filter(String::isNotBlank).joinToString(" · ") },
            icon = Icons.Rounded.AudioFile, highlighted = live && !d.codec.isNullOrBlank(),
            facts = listOf(
                stringResource(R.string.path_origin) to if (pcLibrary) stringResource(R.string.path_source_pc_detail)
                    else stringResource(R.string.path_player_source),
                stringResource(R.string.diag_channels) to (d.channelCount?.takeIf { it > 0 }?.let(::formatChannels) ?: unknown),
                stringResource(R.string.diag_bitrate) to (d.bitrate?.takeIf { it > 0 }?.let(::formatBitrate) ?: unknown),
            ),
        ))
        add(SignalPathStage(
            label = stringResource(R.string.diag_decoder),
            value = when {
                !reported -> stringResource(R.string.diag_waiting_decode)
                d.isDsdDopOutput() -> stringResource(R.string.diag_dsd_dop)
                d.isDsdSource() -> stringResource(R.string.diag_dsd_converted)
                else -> "PCM · ${formatSampleRate(streamRate)}"
            },
            detail = when {
                !reported -> stringResource(R.string.path_format_pending)
                d.isDsdDopOutput() -> stringResource(R.string.diag_dsd_dop_detail, formatSampleRate(streamRate))
                d.isDsdSource() -> stringResource(R.string.diag_dsd_converted_detail, formatSampleRate(streamRate))
                d.decodedSampleRateHz != null && sourceRate != null && sourceRate != streamRate ->
                    stringResource(R.string.path_format_pair, formatSampleRate(sourceRate), formatSampleRate(streamRate))
                else -> stringResource(R.string.path_stream_format_note)
            },
            icon = Icons.Rounded.Memory, highlighted = live && reported,
            facts = listOf(
                stringResource(R.string.path_stream_rate) to (streamRate?.let(::formatSampleRate) ?: unknown),
                stringResource(R.string.path_decoded_bits) to (d.bitPerfectDecodedBits?.takeIf { it > 0 }?.let { "$it bit" } ?: unknown),
            ),
        ))
        add(signalProcessingStage(status, equalizer, balance, dsp))
        add(SignalPathStage(
            label = stringResource(R.string.path_transport),
            value = when {
                d.usbExclusiveStreaming -> stringResource(R.string.diag_usb_exclusive_stream, d.usbExclusiveTransport ?: unknown)
                d.offloadActive -> stringResource(R.string.diag_hw_offload)
                else -> stringResource(R.string.diag_system_path)
            },
            detail = when {
                d.usbExclusiveStreaming -> stringResource(R.string.path_usb_transport_note)
                d.usbExclusiveEnabled && d.usbHostPermissionPending -> stringResource(R.string.diag_usb_wait_auth)
                d.usbExclusiveEnabled -> stringResource(R.string.path_usb_wait_note)
                outputKind == EchoOutputDeviceKind.Bluetooth -> stringResource(R.string.path_bluetooth_note)
                else -> stringResource(R.string.path_system_note)
            },
            icon = if (d.usbExclusiveStreaming) Icons.Rounded.Usb else Icons.Rounded.Route,
            highlighted = live && (d.usbExclusiveStreaming || d.offloadActive),
            facts = buildList {
                add("Bit-perfect" to d.bitPerfectReadout(equalizer))
                if (outputKind == EchoOutputDeviceKind.Bluetooth) add(stringResource(R.string.diag_bluetooth_codec) to
                    (d.bluetoothCodec?.takeIf { it.isNotBlank() } ?: unknown))
                if (d.usbExclusiveEnabled || d.usbConnected) {
                    add(stringResource(R.string.diag_request) to (d.usbLastRequestedSampleRateHz?.takeIf { it > 0 }?.let(::formatSampleRate) ?: unknown))
                    d.usbLastRequestError?.let { add(stringResource(R.string.diag_usb_fallback) to it.message) }
                }
            },
        ))
        add(SignalPathStage(
            label = stringResource(R.string.diag_output_end),
            value = if (d.outputRouteVerified && d.routedDeviceNames.isNotEmpty()) d.routedDeviceNames.joinToString(" · ")
                else outputName ?: outputDeviceKindLabel(outputKind.id),
            detail = stringResource(if (d.outputRouteVerified) R.string.path_route_verified else R.string.path_route_pending),
            icon = when (outputKind) {
                EchoOutputDeviceKind.Usb -> Icons.Rounded.Usb
                EchoOutputDeviceKind.Bluetooth -> Icons.Rounded.Bluetooth
                EchoOutputDeviceKind.Wired -> Icons.Rounded.Headphones
                else -> Icons.Rounded.Speaker
            },
            highlighted = live && d.outputRouteVerified,
            facts = listOf(
                stringResource(R.string.path_output_clock) to
                    (d.bitPerfectSampleRateHz?.takeIf {
                        status.hasVerifiedUsbDirect() && it > 0
                    }?.let(::formatSampleRate) ?: unknown),
                stringResource(R.string.path_output_bits) to
                    (d.bitPerfectOutputBits?.takeIf { status.hasLiveUsbOutput() && d.usbBitPerfectEnabled && it > 0 }?.let { "$it bit" } ?: unknown),
            ),
        ))
    }
}
