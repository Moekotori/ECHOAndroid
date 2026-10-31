package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoSwitch
import app.echo.android.model.playback.*

/** App volume uses dB throughout; system and DAC hardware volume remain separate controls. */
@Composable
internal fun SignalVolumePanel(
    volume: EchoVolumeSettings,
    status: EchoPlaybackStatus,
    dsp: EchoDspSettings,
    scan: EchoReplayGainScanState,
    onVolume: (EchoVolumeSettings) -> Unit,
    onDsp: (EchoDspSettings) -> Unit,
    onReplayGain: (Boolean, Float) -> Unit,
    onMode: (EchoReplayGainMode) -> Unit,
    onScan: () -> Unit,
) {
    val enabled = !status.diagnostics.usbBitPerfectEnabled
    val replayGain = if (status.replayGainEnabled)
        status.replayGainPreampDb + (status.replayGainTrackGainDb ?: 0f) else 0f
    var expanded by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SignalNote(stringResource(R.string.volume_scope))
        if (!enabled) SignalNote(stringResource(R.string.volume_bypassed))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.volume_combined), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(when {
                    !enabled -> stringResource(R.string.bitperfect_bypass)
                    volume.muted -> stringResource(R.string.volume_muted)
                    else -> formatEqGain(volume.combinedGainDb(replayGain))
                }, style = MaterialTheme.typography.headlineMedium)
            }
            TextButton(onClick = { onVolume(EchoVolumeSettings()) }, enabled = enabled) {
                Text(stringResource(R.string.volume_reset))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.volume_mute), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            val muteLabel = stringResource(R.string.volume_mute)
            EchoSwitch(volume.muted, { onVolume(volume.copy(muted = it)) }, enabled = enabled,
                modifier = Modifier.semantics { contentDescription = muteLabel })
        }
        DspValueSlider(stringResource(R.string.volume_level), volume.levelDb, -60f..0f, enabled,
            ::formatEqGain, { onVolume(volume.copy(levelDb = it)) })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { onVolume(volume.adjusted(-1)) }, enabled = enabled && volume.levelDb > -60f) {
                EchoIcon(Icons.AutoMirrored.Rounded.VolumeDown, stringResource(R.string.volume_decrease))
            }
            Text(stringResource(R.string.volume_step_value, signalNumber(volume.stepDb)),
                Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = { onVolume(volume.adjusted(1)) }, enabled = enabled && volume.levelDb < 0f) {
                EchoIcon(Icons.AutoMirrored.Rounded.VolumeUp, stringResource(R.string.volume_increase))
            }
        }
        DspValueSlider(stringResource(R.string.volume_gain), volume.gainDb, -24f..12f, enabled,
            ::formatEqGain, { onVolume(volume.copy(gainDb = it)) })
        if (volume.gainDb > 0f && !dsp.limiterEnabled) SignalNote(stringResource(R.string.volume_gain_hint))
        HorizontalDivider()
        DspValueSlider(stringResource(R.string.volume_limit), volume.limitDb, -24f..30f, enabled,
            ::formatEqGain, { onVolume(volume.copy(limitDb = it)) })
        SignalNote(stringResource(R.string.volume_limit_detail))
        Text(stringResource(R.string.volume_step), style = MaterialTheme.typography.titleSmall)
        DspChoices(EchoVolumeSettings.Steps.map { "${signalNumber(it)} dB" },
            EchoVolumeSettings.Steps.indexOf(volume.stepDb), enabled) {
            onVolume(volume.copy(stepDb = EchoVolumeSettings.Steps[it]))
        }
        SignalDspSection(
            title = stringResource(R.string.dsp_loudness), detail = stringResource(R.string.dsp_loudness_detail),
            summary = stringResource(R.string.volume_replaygain_summary), checked = status.replayGainEnabled,
            enabled = enabled, expanded = expanded == "loudness",
            onExpand = { expanded = if (expanded == "loudness") "" else "loudness" },
            onChecked = { onReplayGain(it, status.replayGainPreampDb) },
            onReset = { onReplayGain(false, 0f); onMode(EchoReplayGainMode.Auto) },
        ) { SignalReplayGainControls(status, scan, enabled, onReplayGain, onMode, onScan) }
        SignalDspSection(
            title = stringResource(R.string.dsp_limiter), detail = stringResource(R.string.dsp_limiter_detail),
            summary = "${signalNumber(dsp.limiterCeilingDb)} dBFS", checked = dsp.limiterEnabled,
            enabled = enabled, expanded = expanded == "limiter",
            onExpand = { expanded = if (expanded == "limiter") "" else "limiter" },
            onChecked = { onDsp(dsp.copy(limiterEnabled = it)) },
            onReset = { onDsp(dsp.copy(limiterEnabled = false, limiterCeilingDb = -1f)) },
        ) {
            DspValueSlider(stringResource(R.string.dsp_output_ceiling), dsp.limiterCeilingDb, -6f..-0.1f, enabled,
                { "${signalNumber(it)} dBFS" }, { onDsp(dsp.copy(limiterCeilingDb = it)) }, unit = "dBFS")
        }
    }
}
