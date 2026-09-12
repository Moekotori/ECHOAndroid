package app.echo.android.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.model.playback.*

private data class PathStage(
    val label: String,
    val value: String,
    val detail: String?,
    val icon: ImageVector,
    val active: Boolean,
)

@Composable
internal fun SignalPathPanel(
    status: EchoPlaybackStatus,
    equalizer: EchoEqualizerState,
    balance: EchoChannelBalanceState,
) {
    val d = status.diagnostics
    val scheme = MaterialTheme.colorScheme
    val live = status.isPlaying && status.state != EchoPlaybackState.Error
    val decoded = live && (d.decodedSampleRateHz ?: 0) > 0
    val eqActive = equalizer.active && equalizer.available && !d.usbBitPerfectEnabled
    val balanceActive = balance.active && !d.usbBitPerfectEnabled
    // A selected route or enabled switch is not evidence of bit-perfect transport.
    val verified = live && d.usbExclusiveStreaming && d.bitPerfectState == EchoBitPerfectState.Direct
    val processing = decoded && (eqActive || balanceActive)
    val label = when {
        status.state == EchoPlaybackState.Error -> playbackStateLabel(status.state)
        !live -> playbackStateLabel(status.state)
        verified -> stringResource(R.string.path_verified)
        processing -> stringResource(R.string.path_processing)
        else -> stringResource(R.string.path_unverified)
    }
    val dark = LocalEchoDarkTheme.current
    val accent = when {
        status.state == EchoPlaybackState.Error -> scheme.error
        !live -> scheme.onSurfaceVariant
        verified -> if (dark) Color(0xFFD0B5FF) else Color(0xFF6842A6)
        processing -> if (dark) Color(0xFF9FCBFA) else Color(0xFF265E91)
        else -> scheme.onSurfaceVariant
    }
    val explanation = when {
        status.state == EchoPlaybackState.Error -> stringResource(R.string.path_error_detail)
        !live -> stringResource(R.string.path_pending_detail)
        verified -> d.bitPerfectReadout(equalizer)
        else -> stringResource(R.string.path_unverified_detail)
    }
    val unknown = stringResource(R.string.diag_unreported)
    val stages = buildList {
        add(PathStage(
            stringResource(R.string.diag_source_file),
            if (status.track != null) d.fileFormatLabel() else stringResource(R.string.diag_waiting_playback),
            listOfNotNull(status.track?.title, d.channelCount?.takeIf { it > 0 }?.let(::formatChannels)).joinToString(" · ").ifBlank { null },
            Icons.Rounded.AudioFile,
            live && !d.codec.isNullOrBlank(),
        ))
        add(PathStage(
            stringResource(R.string.diag_decoder),
            when {
                !decoded -> stringResource(R.string.diag_waiting_decode)
                d.isDsdDopOutput() -> stringResource(R.string.diag_dsd_dop)
                d.isDsdSource() -> stringResource(R.string.diag_dsd_converted)
                else -> d.decodedFormatLabel()
            },
            when {
                !decoded -> null
                d.isDsdDopOutput() || d.isDsdSource() -> d.decodedFormatLabel()
                else -> null
            },
            Icons.Rounded.Memory,
            decoded,
        ))
        val sourceRate = d.sampleRateHz
        val decodedRate = d.decodedSampleRateHz
        if (decoded && !d.isDsdSource() && sourceRate != null && sourceRate > 0 && decodedRate != null && sourceRate != decodedRate) {
            add(PathStage(
                stringResource(R.string.path_format_change),
                stringResource(R.string.path_format_pair, formatSampleRate(sourceRate), formatSampleRate(decodedRate)),
                null, Icons.Rounded.SwapVert, true,
            ))
        }
        if (eqActive) add(PathStage(
            stringResource(R.string.feature_settings_equalizer_7ccb03),
            equalizer.sourceLabel ?: equalizer.presetName,
            stringResource(R.string.path_eq_detail, formatEqGain(equalizer.preampDb), equalizer.processingSampleRateHz?.let(::formatSampleRate) ?: unknown),
            Icons.Rounded.Tune, decoded,
        ))
        if (balanceActive) add(PathStage(
            stringResource(R.string.channel_balance),
            stringResource(R.string.path_channel_detail, formatEqGain(balance.leftGainDb), formatEqGain(balance.rightGainDb)),
            channelPathDetail(balance), Icons.Rounded.SwapHoriz, decoded,
        ))
        if (!eqActive && !balanceActive) add(PathStage(
            stringResource(R.string.diag_processing_layer),
            if (d.usbBitPerfectEnabled) stringResource(R.string.bitperfect_bypass) else d.processingLabel(),
            if (d.usbBitPerfectEnabled) stringResource(R.string.bitperfect_bypass_detail) else stringResource(R.string.feature_settings_equalizer_is_not_changing_the_signal_7cf73b),
            Icons.Rounded.Tune, decoded,
        ))
        add(PathStage(
            stringResource(R.string.diag_output_end),
            when {
                d.usbExclusiveStreaming -> stringResource(R.string.diag_usb_exclusive_stream, d.usbExclusiveTransport ?: "PCM")
                d.usbHostPermissionPending -> stringResource(R.string.diag_usb_wait_auth)
                else -> outputDeviceKindLabel(d.outputDeviceKind)
            },
            listOfNotNull(
                (if (d.usbExclusiveStreaming) d.usbDeviceName else d.outputDeviceName)?.takeIf { it.isNotBlank() },
                d.bluetoothCodec?.takeIf { EchoOutputDeviceKind.fromId(d.outputDeviceKind) == EchoOutputDeviceKind.Bluetooth && it.isNotBlank() },
                d.bitPerfectSampleRateHz?.takeIf { verified && it > 0 }?.let { stringResource(R.string.path_device_clock, formatSampleRate(it)) },
            ).joinToString(" · ").ifBlank { null },
            Icons.Rounded.Speaker, live && d.usbExclusiveStreaming,
        ))
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainerLow,
        contentColor = scheme.onSurface,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.36f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(stringResource(R.string.feature_settings_signal_path_2fed34), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(7.dp).background(accent, CircleShape))
                Text(label, color = accent, style = MaterialTheme.typography.labelLarge)
            }
            SignalNote(explanation)
            HorizontalDivider(Modifier.padding(vertical = 18.dp), color = scheme.outlineVariant.copy(alpha = 0.40f))
            stages.forEachIndexed { index, stage ->
                SignalPathStep(
                    index = (index + 1).toString().padStart(2, '0'),
                    label = stage.label,
                    value = stage.value,
                    detail = stage.detail,
                    active = stage.active,
                    last = index == stages.lastIndex,
                    icon = stage.icon,
                )
            }
        }
    }
}

@Composable
private fun channelPathDetail(state: EchoChannelBalanceState): String {
    val b = state.normalized
    return buildList {
        if (kotlin.math.abs(b.balance) > 0.001f) add(stringResource(
            R.string.channel_balance_bias,
            stringResource(if (b.balance < 0) R.string.channel_balance_left else R.string.channel_balance_right),
            (kotlin.math.abs(b.balance) * 100).toInt(),
        ))
        if (b.swapLeftRight) add(stringResource(R.string.channel_balance_swap))
        if (b.invertLeft) add(stringResource(R.string.channel_balance_invert_left))
        if (b.invertRight) add(stringResource(R.string.channel_balance_invert_right))
        if (b.monoMode != EchoChannelBalanceMonoMode.Off) add(stringResource(when (b.monoMode) {
            EchoChannelBalanceMonoMode.Left -> R.string.channel_balance_mono_left
            EchoChannelBalanceMonoMode.Right -> R.string.channel_balance_mono_right
            else -> R.string.channel_balance_mono_sum
        }))
        if (b.leftDelayMs > 0) add(stringResource(R.string.channel_balance_delay_left) + " · " + stringResource(R.string.channel_balance_delay_ms, b.leftDelayMs))
        if (b.rightDelayMs > 0) add(stringResource(R.string.channel_balance_delay_right) + " · " + stringResource(R.string.channel_balance_delay_ms, b.rightDelayMs))
        if (b.leftBandGainsDb.any { kotlin.math.abs(it) >= 0.05f } || b.rightBandGainsDb.any { kotlin.math.abs(it) >= 0.05f }) {
            add(stringResource(R.string.channel_balance_bands))
        }
    }.joinToString(" · ")
}
