package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.playback.EchoPlaybackDiagnostics

@Composable
internal fun UsbOutputDetails(d: EchoPlaybackDiagnostics, output: UsbOutputPresentation) {
    val unknown = stringResource(R.string.diag_unreported)
    val rates = remember(d.usbSupportedSampleRates, output.connected) {
        if (output.connected) d.usbSupportedSampleRates.filter { it > 0 }.distinct().sorted()
            .joinToString(" / ", transform = ::formatSampleRate) else ""
    }
    val rows = listOf(
        stringResource(R.string.diag_device) to (output.deviceName ?: stringResource(R.string.diag_no_usb)),
        stringResource(R.string.diag_usb_permission) to stringResource(when {
            output.permissionPending -> R.string.diag_waiting_confirm
            output.permissionGranted -> R.string.diag_authorized
            output.connected -> R.string.diag_unauthorized
            else -> R.string.diag_not_requested_short
        }),
        stringResource(R.string.path_output_clock) to (output.clockHz?.let(::formatSampleRate) ?: unknown),
        stringResource(R.string.path_output_bits) to (output.outputBits?.let { "$it bit" } ?: unknown),
        stringResource(R.string.dac_requested_rate) to (d.usbLastRequestedSampleRateHz
            ?.takeIf { output.connected && it > 0 }?.let(::formatSampleRate) ?: unknown),
        stringResource(R.string.usb_param_transfer_rate) to (output.transferRateHz?.let(::formatSampleRate) ?: unknown),
        stringResource(R.string.dac_source_precision) to (
            (d.bitPerfectSourceBits?.takeIf { output.verified } ?: d.bitDepth)?.takeIf { it > 0 }
                ?.let { "$it bit" } ?: unknown),
        stringResource(R.string.path_decoded_bits) to (d.bitPerfectDecodedBits
            ?.takeIf { output.verified && it > 0 }?.let { "$it bit" } ?: unknown),
        stringResource(R.string.dac_reported_rates) to rates.ifBlank { unknown },
    )
    val transportRows = usbTransportRows(d)
    rows.forEach { (label, value) -> SignalReadout(label, value) }
    transportRows.forEach { (label, value) -> SignalReadout(label, value) }
    if (d.usbTransfer != null) SignalNote(stringResource(R.string.usb_health_estimate_note))
    if (d.usbCompatibilityRules.isNotEmpty()) SignalNote(stringResource(R.string.usb_health_teac_note))
    if (output.connected) {
        output.audioClass?.let { SignalReadout("UAC", it) }
        d.usbAudioEndpointSummary?.let { SignalReadout(stringResource(R.string.diag_endpoint), it) }
        d.usbExclusiveTransport?.takeIf { output.streaming }?.let {
            SignalReadout(stringResource(R.string.diag_transport), it)
        }
        val error = d.usbLastRequestError?.message ?: d.usbAudioDescriptorError
            ?: d.lastError?.message?.takeIf { output.failed }
        error?.takeIf(String::isNotBlank)?.let {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text(stringResource(R.string.usb_page_current_error), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error)
            SelectionContainer {
                Text(it.take(2_000), style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val evidenceNote = stringResource(R.string.dac_evidence_note)
    val ratesNote = stringResource(R.string.dac_rates_note)
    SignalNote(evidenceNote)
    SignalNote(ratesNote)
    val title = stringResource(R.string.settings_usb_exclusive)
    val state = stringResource(output.headlineResource())
    // Copy only device/output facts, never track paths, credentials or raw exception messages.
    val report = buildString {
        appendLine(title)
        appendLine(state)
        rows.forEach { (label, value) -> appendLine("$label: $value") }
        transportRows.forEach { (label, value) -> appendLine("$label: $value") }
        d.usbVolume?.let { appendLine("USB volume: $it") }
        d.usbCompatibilityRules.forEach { appendLine("${it.id}: ${it.reason} ${it.verification}") }
        appendLine(evidenceNote)
        append(ratesNote)
    }
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    var copied by remember(report) { mutableStateOf(false) }
    TextButton(onClick = {
        clipboardScope.launch {
            clipboard.setClipEntry(ClipEntry(android.content.ClipData.newPlainText("ECHO", report)))
        }
        copied = true
    },
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EchoIcon(if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, null,
                Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(if (copied) R.string.dac_report_copied else R.string.dac_copy_report),
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
internal fun UsbDriverTestFeedback(result: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.usb_page_test_result), style = MaterialTheme.typography.labelLarge)
        SelectionContainer {
            Text(result.take(2_000), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
