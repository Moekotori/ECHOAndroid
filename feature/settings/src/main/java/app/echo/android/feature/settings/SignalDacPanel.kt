package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.HorizontalDivider
import app.echo.android.design.EchoIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoPlaybackStatus

@Composable
internal fun SignalDacPanel(status: EchoPlaybackStatus, onDiagnostics: () -> Unit) {
    val d = status.diagnostics
    val assessment = assessDac(status)
    val live = status.hasLiveUsbOutput()
    val verified = status.hasVerifiedUsbDirect()
    val unknown = stringResource(R.string.diag_unreported)
    val title = stringResource(R.string.dac_check_title)
    val guidance = stringResource(assessment.guidanceResource())
    val headline = stringResource(when {
        verified -> R.string.dac_status_direct
        live -> R.string.dac_status_exclusive
        else -> R.string.dac_status_unverified
    })
    val compactHeadline = stringResource(when {
        verified -> R.string.dac_status_direct_short
        live -> R.string.dac_status_exclusive_short
        else -> R.string.dac_status_unverified_short
    })
    val scheme = MaterialTheme.colorScheme
    val accent = when (assessment) {
        DacAssessment.PlaybackError, DacAssessment.TransportError, DacAssessment.UnsupportedFormat,
        DacAssessment.UnsupportedSource -> scheme.error
        DacAssessment.Direct -> scheme.primary
        else -> scheme.onSurfaceVariant
    }
    val reportedRates = remember(d.usbSupportedSampleRates) {
        d.usbSupportedSampleRates.filter { it > 0 }.distinct().sorted()
            .joinToString(" / ", transform = ::formatSampleRate)
    }
    val rows = listOf(
        stringResource(R.string.diag_device) to
            (d.usbDeviceName?.takeIf { d.usbConnected && it.isNotBlank() } ?: stringResource(R.string.diag_no_usb)),
        stringResource(R.string.diag_usb_permission) to stringResource(when {
            !d.usbConnected -> R.string.diag_not_requested_short
            d.usbHostPermissionGranted -> R.string.diag_authorized
            d.usbHostPermissionPending -> R.string.diag_waiting_confirm
            else -> R.string.diag_unauthorized
        }),
        stringResource(R.string.path_output_clock) to
            (d.bitPerfectSampleRateHz?.takeIf { verified && it > 0 }?.let(::formatSampleRate) ?: unknown),
        stringResource(R.string.dac_requested_rate) to
            (d.usbLastRequestedSampleRateHz?.takeIf { d.usbConnected && it > 0 }?.let(::formatSampleRate) ?: unknown),
    )
    val precisionRows = listOf(
        stringResource(R.string.dac_source_precision) to
            (d.bitPerfectSourceBits?.takeIf { live && d.usbBitPerfectEnabled && it > 0 }?.let { "$it bit" } ?: unknown),
        stringResource(R.string.path_decoded_bits) to
            (d.bitPerfectDecodedBits?.takeIf { live && d.usbBitPerfectEnabled && it > 0 }?.let { "$it bit" } ?: unknown),
        stringResource(R.string.path_output_bits) to
            (d.bitPerfectOutputBits?.takeIf { live && d.usbBitPerfectEnabled && it > 0 }?.let { "$it bit" } ?: unknown),
        stringResource(R.string.dac_reported_rates) to reportedRates.takeIf { d.usbConnected && it.isNotBlank() }.orEmpty().ifBlank { unknown },
    )
    val evidenceNote = stringResource(R.string.dac_evidence_note)
    val ratesNote = stringResource(R.string.dac_rates_note)
    val rateChanged = status.hasDecodedRateChange()
    val rateNote = if (rateChanged) stringResource(R.string.dac_decoded_rate_changed,
        formatSampleRate(requireNotNull(d.sampleRateHz)), formatSampleRate(requireNotNull(d.decodedSampleRateHz))) else null
    // Explicit clipboard action only; no track paths, URIs, pairing tokens or library data.
    val report = buildString {
        appendLine(title)
        appendLine(headline)
        appendLine(guidance)
        (rows + precisionRows).forEach { (label, value) -> appendLine("$label: $value") }
        rateNote?.let { appendLine(it) }
        appendLine(evidenceNote)
        append(ratesNote)
    }
    val clipboard = LocalClipboardManager.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var copiedReport by remember { mutableStateOf<String?>(null) }
    SignalSection(title) {
        SignalDacDeviceHeader(rows[0].second, d.usbAudioClass?.takeIf { d.usbConnected }, compactHeadline, accent)
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SignalDacOutputMetrics(rows[2], precisionRows[2], unknown)
            SignalDacAdvice(stringResource(if (verified) R.string.dac_path_note_title else R.string.dac_next_step_title), guidance, accent)
        }
        rateNote?.let { SignalNote(it) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDiagnostics, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 10.dp)) {
                Text(stringResource(R.string.feature_settings_view_diagnostics_31fcec), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                EchoIcon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            TextButton(onClick = { clipboard.setText(AnnotatedString(report)); copiedReport = report },
                modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 0.dp, vertical = 10.dp)) {
                EchoIcon(if (copiedReport == report) Icons.Rounded.Check else Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (copiedReport == report) R.string.dac_report_copied else R.string.dac_copy_report), modifier = Modifier.weight(1f))
            }
        }
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.55f))
        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 0.dp)) {
            EchoIcon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
            Text(stringResource(if (expanded) R.string.dac_hide_capabilities else R.string.dac_show_capabilities))
        }
        if (expanded) {
            SignalReadout(rows[1].first, rows[1].second)
            SignalReadout(rows[3].first, rows[3].second)
            precisionRows.filterIndexed { index, _ -> index != 2 }.forEach { (label, value) -> SignalReadout(label, value) }
            SignalNote(evidenceNote)
            SignalNote(ratesNote)
        }
    }
}
