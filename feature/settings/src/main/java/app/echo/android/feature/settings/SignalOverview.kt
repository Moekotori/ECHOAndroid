package app.echo.android.feature.settings

import app.echo.android.feature.settings.R as L10nR

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.*

@Composable
internal fun SignalOverview(status: EchoPlaybackStatus, equalizer: EchoEqualizerState, onAdjust: () -> Unit, onDiagnostics: () -> Unit) {
    val d = status.diagnostics
    val hasSource = status.track != null || status.isPlaying
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SignalNote(stringResource(R.string.diag_output_end))
            Text(d.usbDeviceName ?: d.outputRoute, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
            SignalNote(if (hasSource) d.signalIntegrityLabel(equalizer) else stringResource(R.string.diag_pick_track))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SignalMetric(stringResource(R.string.diag_sample_rate), d.sampleRateHz?.let(::formatSampleRate) ?: "—", Modifier.weight(1f))
            SignalMetric(stringResource(R.string.diag_bit_depth), d.bitDepth?.let { "$it bit" } ?: "—", Modifier.weight(1f))
            SignalMetric(stringResource(R.string.diag_channels), d.channelCount?.let(::formatChannels) ?: "—", Modifier.weight(1f))
        }
        SignalSection(stringResource(L10nR.string.feature_settings_signal_path_2fed34)) {
            SignalPathStep("01", stringResource(R.string.diag_source_file), if (hasSource) d.fileFormatLabel() else stringResource(R.string.diag_waiting_playback), status.track?.title, hasSource)
            SignalPathStep("02", stringResource(R.string.diag_decoder), d.codec ?: stringResource(R.string.diag_waiting_decode), if (hasSource) d.decodedFormatLabel() else null, hasSource)
            SignalPathStep("03", stringResource(R.string.diag_processing_layer), if (equalizer.active) equalizer.sourceLabel ?: equalizer.presetName else d.processingLabel(),
                if (equalizer.active) stringResource(R.string.diag_integrity_eq) else stringResource(L10nR.string.feature_settings_equalizer_is_not_changing_the_signal_7cf73b), equalizer.active)
            SignalPathStep("04", stringResource(R.string.diag_output_end), when {
                d.usbExclusiveStreaming -> stringResource(R.string.diag_usb_exclusive_stream, d.usbExclusiveTransport ?: "PCM")
                d.usbBitPerfectActive -> stringResource(R.string.diag_usb_bit_perfect)
                d.usbHostPermissionPending -> stringResource(R.string.diag_usb_wait_auth)
                else -> d.outputRoute
            }, d.usbDeviceName, hasSource, last = true)
        }
        SignalSection(stringResource(L10nR.string.feature_settings_output_details_f242d2)) {
            SignalReadout(stringResource(R.string.diag_decoded_output), d.decodedSampleRateHz?.let(::formatSampleRate) ?: stringResource(R.string.diag_unreported))
            SignalReadout(stringResource(R.string.diag_bitrate), d.bitrate?.let(::formatBitrate) ?: stringResource(R.string.diag_unreported))
            SignalReadout("Bit-perfect", if (hasSource) d.bitPerfectReadout(equalizer) else stringResource(R.string.diag_waiting_playback))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onAdjust) { Text(stringResource(L10nR.string.feature_settings_adjust_sound_f8940e)) }
                TextButton(onClick = onDiagnostics) { Text(stringResource(L10nR.string.feature_settings_view_diagnostics_31fcec)) }
            }
        }
    }
}

@Composable
private fun SignalPathStep(index: String, label: String, value: String, detail: String?, active: Boolean, last: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(28.dp).fillMaxHeight(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text(index, style = MaterialTheme.typography.labelMedium, color = if (active) scheme.primary else scheme.onSurfaceVariant)
            if (!last) Box(Modifier.padding(top = 8.dp).width(1.dp).weight(1f).background(scheme.outlineVariant))
        }
        Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            SignalNote(label)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (!detail.isNullOrBlank()) SignalNote(detail)
        }
    }
}
