package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.*

@Composable
internal fun SignalDspPanel(
    settings: EchoDspSettings, status: EchoPlaybackStatus, scan: EchoReplayGainScanState,
    onSettings: (EchoDspSettings) -> Unit, onReplayGain: (Boolean, Float) -> Unit,
    onMode: (EchoReplayGainMode) -> Unit, onScan: () -> Unit,
) {
    val bypassed = status.diagnostics.usbBitPerfectEnabled
    var expanded by rememberSaveable { mutableStateOf("limiter") }
    val enabledCount = (if (settings.limiterEnabled) 1 else 0) +
        (if (settings.crossfeedEnabled) 1 else 0) + (if (status.replayGainEnabled) 1 else 0)
    val defaults = remember { EchoDspSettings() }
    val modeLabel = stringResource(when (status.replayGainMode) {
        EchoReplayGainMode.Auto -> R.string.dsp_auto
        EchoReplayGainMode.Track -> R.string.dsp_track
        EchoReplayGainMode.Album -> R.string.dsp_album
    })
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (bypassed) stringResource(R.string.signal_bypass_locked) else stringResource(R.string.signal_dsp_enabled_count, enabledCount), Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(enabled = !bypassed, onClick = {
                onSettings(defaults)
                onReplayGain(false, 0f)
                onMode(EchoReplayGainMode.Auto)
            }) { Text(stringResource(R.string.signal_reset_dsp)) }
        }
        if (bypassed) SignalNote(stringResource(R.string.signal_bypass_detail))
        SignalDspSection(
            title = stringResource(R.string.dsp_loudness), detail = stringResource(R.string.dsp_loudness_detail),
            summary = "$modeLabel · ${formatEqGain(status.replayGainPreampDb)}",
            checked = status.replayGainEnabled, enabled = !bypassed, expanded = expanded == "loudness",
            onExpand = { expanded = if (expanded == "loudness") "" else "loudness" },
            onChecked = { onReplayGain(it, status.replayGainPreampDb); if (it) expanded = "loudness" },
            onReset = { onReplayGain(false, 0f); onMode(EchoReplayGainMode.Auto) },
        ) {
            SignalReplayGainControls(status, scan, !bypassed, onReplayGain, onMode, onScan)
        }
        SignalDspSection(
            title = stringResource(R.string.dsp_crossfeed), detail = stringResource(R.string.dsp_crossfeed_detail),
            summary = stringResource(R.string.signal_crossfeed_summary, signalNumber(settings.crossfeedAmount * 100, 0)),
            checked = settings.crossfeedEnabled, enabled = !bypassed, expanded = expanded == "crossfeed",
            onExpand = { expanded = if (expanded == "crossfeed") "" else "crossfeed" },
            onChecked = { onSettings(settings.copy(crossfeedEnabled = it)); if (it) expanded = "crossfeed" },
            onReset = { onSettings(settings.copy(crossfeedEnabled = defaults.crossfeedEnabled, crossfeedAmount = defaults.crossfeedAmount)) },
        ) {
            val amounts = listOf(0.15f, 0.3f, 0.5f)
            DspChoices(listOf(stringResource(R.string.dsp_gentle), stringResource(R.string.dsp_natural), stringResource(R.string.dsp_strong)),
                amounts.indexOfFirst { kotlin.math.abs(it - settings.crossfeedAmount) < 0.005f }, !bypassed) {
                onSettings(settings.copy(crossfeedAmount = amounts[it]))
            }
            DspValueSlider(stringResource(R.string.dsp_mix_amount), settings.crossfeedAmount, 0f..0.6f, !bypassed,
                { "${signalNumber(it * 100, 0)}%" }, { onSettings(settings.copy(crossfeedAmount = it)) },
                unit = "%", scale = 100f, decimals = 0)
        }
        SignalDspSection(
            title = stringResource(R.string.dsp_limiter), detail = stringResource(R.string.dsp_limiter_detail),
            summary = "${signalNumber(settings.limiterCeilingDb)} dBFS",
            checked = settings.limiterEnabled, enabled = !bypassed, expanded = expanded == "limiter",
            onExpand = { expanded = if (expanded == "limiter") "" else "limiter" },
            onChecked = { onSettings(settings.copy(limiterEnabled = it)); if (it) expanded = "limiter" },
            onReset = { onSettings(settings.copy(limiterEnabled = defaults.limiterEnabled, limiterCeilingDb = defaults.limiterCeilingDb)) },
        ) {
            DspValueSlider(stringResource(R.string.dsp_output_ceiling), settings.limiterCeilingDb, -6f..-0.1f, !bypassed,
                { "${signalNumber(it)} dBFS" }, { onSettings(settings.copy(limiterCeilingDb = it)) }, unit = "dBFS")
        }
    }
}
