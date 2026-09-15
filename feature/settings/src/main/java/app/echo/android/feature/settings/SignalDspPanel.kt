package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoExpand
import app.echo.android.design.EchoSwitch
import app.echo.android.model.playback.*
import java.util.Locale

@Composable
internal fun SignalDspPanel(settings: EchoDspSettings, status: EchoPlaybackStatus, scan: EchoReplayGainScanState,
    onSettings: (EchoDspSettings) -> Unit, onReplayGain: (Boolean, Float) -> Unit,
    onMode: (EchoReplayGainMode) -> Unit, onScan: () -> Unit,
) {
    val bypassed = status.diagnostics.usbBitPerfectEnabled
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (bypassed) SignalNote(stringResource(R.string.eq_bypassed))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                DspSection(stringResource(R.string.dsp_limiter), stringResource(R.string.dsp_limiter_detail), settings.limiterEnabled, !bypassed, { onSettings(settings.copy(limiterEnabled = it)) }) {
                    DspValueSlider(stringResource(R.string.dsp_output_ceiling), settings.limiterCeilingDb, -6f..-0.1f, !bypassed && settings.limiterEnabled,
                        { String.format(Locale.getDefault(), "%.1f dBFS", it) }, { onSettings(settings.copy(limiterCeilingDb = it)) })
                }
                DspDivider()
                DspSection(stringResource(R.string.dsp_loudness), stringResource(R.string.dsp_loudness_detail), status.replayGainEnabled, !bypassed, { onReplayGain(it, status.replayGainPreampDb) }) {
                    val modes = listOf(EchoReplayGainMode.Auto, EchoReplayGainMode.Track, EchoReplayGainMode.Album)
                    DspChoices(listOf(stringResource(R.string.dsp_auto), stringResource(R.string.dsp_track), stringResource(R.string.dsp_album)), modes.indexOf(status.replayGainMode), !bypassed) { onMode(modes[it]) }
                    Text(if (status.replayGainTrackGainDb != null) stringResource(R.string.dsp_gain, status.replayGainTrackGainDb!!) else stringResource(R.string.dsp_missing_tags),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DspValueSlider(stringResource(R.string.eq_preamp), status.replayGainPreampDb.coerceIn(-12f, 6f), -12f..6f, !bypassed && status.replayGainEnabled, ::formatEqGain) { onReplayGain(status.replayGainEnabled, it) }
                    TextButton(onClick = onScan, enabled = status.track != null && scan != EchoReplayGainScanState.Scanning, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(stringResource(R.string.dsp_scan_short)) }
                    when (scan) {
                        EchoReplayGainScanState.Scanning -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        is EchoReplayGainScanState.Written -> Text(stringResource(R.string.dsp_scanned, scan.gainDb), style = MaterialTheme.typography.bodySmall)
                        is EchoReplayGainScanState.Failed -> SignalNote(stringResource(R.string.dsp_scan_failed), error = true)
                        else -> Unit
                    }
                }
                DspDivider()
                DspSection(stringResource(R.string.dsp_crossfeed), stringResource(R.string.dsp_crossfeed_detail), settings.crossfeedEnabled, !bypassed, { onSettings(settings.copy(crossfeedEnabled = it)) }) {
                    val amounts = listOf(0.15f, 0.3f, 0.5f)
                    DspChoices(listOf(stringResource(R.string.dsp_gentle), stringResource(R.string.dsp_natural), stringResource(R.string.dsp_strong)), amounts.indexOfFirst { kotlin.math.abs(it - settings.crossfeedAmount) < 0.01f }, !bypassed) { onSettings(settings.copy(crossfeedAmount = amounts[it])) }
                    DspValueSlider(stringResource(R.string.dsp_mix_amount), settings.crossfeedAmount, 0f..0.6f, !bypassed && settings.crossfeedEnabled,
                        { String.format(Locale.getDefault(), "%.0f%%", it * 100) }, { onSettings(settings.copy(crossfeedAmount = it)) })
                }
            }
        }
    }
}

@Composable
private fun DspDivider() { HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)) }

@Composable
private fun DspSection(title: String, detail: String, checked: Boolean, enabled: Boolean, onChecked: (Boolean) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    var help by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { help = !help }) { Icon(Icons.Outlined.Info, contentDescription = detail, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            EchoSwitch(checked = checked, onCheckedChange = onChecked, enabled = enabled, modifier = Modifier.semantics { contentDescription = title })
        }
        EchoExpand(help) { Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        content()
    }
}
