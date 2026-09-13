package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.*

@Composable
internal fun SignalDspPanel(
    settings: EchoDspSettings,
    status: EchoPlaybackStatus,
    scan: EchoReplayGainScanState,
    onSettings: (EchoDspSettings) -> Unit,
    onReplayGain: (Boolean, Float) -> Unit,
    onMode: (EchoReplayGainMode) -> Unit,
    onScan: () -> Unit,
) {
    val bypassed = status.diagnostics.usbBitPerfectEnabled
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (bypassed) SignalNote(stringResource(R.string.eq_bypassed))
        DspSection(stringResource(R.string.dsp_limiter), stringResource(R.string.dsp_limiter_detail), settings.limiterEnabled, !bypassed,
            { onSettings(settings.copy(limiterEnabled = it)) }) {
            Text(stringResource(R.string.dsp_ceiling, settings.limiterCeilingDb), style = MaterialTheme.typography.labelLarge)
            DspSlider(value = settings.limiterCeilingDb, onCommit = { onSettings(settings.copy(limiterCeilingDb = it)) }, valueRange = -6f..-0.1f, enabled = !bypassed && settings.limiterEnabled)
        }
        DspSection(stringResource(R.string.dsp_loudness), stringResource(R.string.dsp_loudness_detail), status.replayGainEnabled, !bypassed,
            { onReplayGain(it, status.replayGainPreampDb) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(EchoReplayGainMode.Auto to R.string.dsp_auto, EchoReplayGainMode.Track to R.string.dsp_track, EchoReplayGainMode.Album to R.string.dsp_album).forEach { (mode, label) ->
                    FilterChip(selected = status.replayGainMode == mode, onClick = { onMode(mode) }, label = { Text(stringResource(label)) }, enabled = !bypassed)
                }
            }
            Text(if (status.replayGainTrackGainDb != null) stringResource(R.string.dsp_gain, status.replayGainTrackGainDb!!) else stringResource(R.string.dsp_missing_tags), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.dsp_gain, status.replayGainPreampDb), style = MaterialTheme.typography.labelLarge)
            DspSlider(value = status.replayGainPreampDb.coerceIn(-12f, 6f), onCommit = { onReplayGain(status.replayGainEnabled, it) }, valueRange = -12f..6f, enabled = !bypassed && status.replayGainEnabled)
            TextButton(onClick = onScan, enabled = status.track != null && scan != EchoReplayGainScanState.Scanning) { Text(stringResource(R.string.dsp_scan)) }
            when (scan) {
                EchoReplayGainScanState.Scanning -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is EchoReplayGainScanState.Written -> Text(stringResource(R.string.dsp_scanned, scan.gainDb))
                is EchoReplayGainScanState.Failed -> SignalNote(stringResource(R.string.dsp_scan_failed), error = true)
                else -> Unit
            }
        }
        DspSection(stringResource(R.string.dsp_crossfeed), stringResource(R.string.dsp_crossfeed_detail), settings.crossfeedEnabled, !bypassed,
            { onSettings(settings.copy(crossfeedEnabled = it)) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.15f to R.string.dsp_gentle, 0.3f to R.string.dsp_natural, 0.5f to R.string.dsp_strong).forEach { (amount, label) ->
                    FilterChip(selected = kotlin.math.abs(settings.crossfeedAmount - amount) < 0.01f, onClick = { onSettings(settings.copy(crossfeedAmount = amount)) }, label = { Text(stringResource(label)) }, enabled = !bypassed)
                }
            }
            DspSlider(value = settings.crossfeedAmount, onCommit = { onSettings(settings.copy(crossfeedAmount = it)) }, valueRange = 0f..0.6f, enabled = !bypassed && settings.crossfeedEnabled)
        }
    }
}

@Composable
private fun DspSection(title: String, detail: String, checked: Boolean, enabled: Boolean, onChecked: (Boolean) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    SignalEqWell(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun DspSlider(value: Float, onCommit: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, enabled: Boolean) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    Slider(value = draft, onValueChange = { draft = it }, onValueChangeFinished = { onCommit(draft) }, valueRange = valueRange, enabled = enabled)
}
