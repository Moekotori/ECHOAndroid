package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoSwitch
import app.echo.android.design.EchoExpand
import app.echo.android.design.echoClickable
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
    deviceLabel: String? = null,
) {
    val enabled = !status.diagnostics.usbBitPerfectEnabled
    val replayGain = if (status.replayGainEnabled)
        status.replayGainPreampDb + (status.replayGainTrackGainDb ?: 0f) else 0f
    var expanded by rememberSaveable { mutableStateOf("") }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(deviceLabel ?: stringResource(R.string.volume_current_output),
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(if (volume.rememberPerDevice) R.string.volume_device_saved else R.string.volume_shared), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.volume_mute), Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelMedium)
            val muteLabel = stringResource(R.string.volume_mute)
            EchoSwitch(volume.muted, { onVolume(volume.copy(muted = it)) }, enabled = enabled,
                modifier = Modifier.semantics { contentDescription = muteLabel })
        }
        if (!enabled) SignalNote(stringResource(R.string.volume_bypassed))
        DspValueSlider(stringResource(R.string.volume_level), volume.levelDb, -60f..0f, enabled,
            ::formatEqGain, { onVolume(volume.copy(levelDb = it)) })
        DspValueSlider(stringResource(R.string.volume_gain), volume.gainDb, -24f..12f, enabled,
            ::formatEqGain, { onVolume(volume.copy(gainDb = it)) })
        if (volume.gainDb > 0f && !dsp.limiterEnabled) SignalNote(stringResource(R.string.volume_gain_hint))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.volume_step), style = MaterialTheme.typography.titleSmall)
            DspChoices(EchoVolumeSettings.Steps.map { "${signalNumber(it)} dB" },
                EchoVolumeSettings.Steps.indexOf(volume.stepDb), enabled) {
                onVolume(volume.copy(stepDb = EchoVolumeSettings.Steps[it]))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val rememberLabel = stringResource(R.string.volume_remember_per_device)
            Text(rememberLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            EchoSwitch(volume.rememberPerDevice, { onVolume(volume.copy(rememberPerDevice = it)) },
                modifier = Modifier.semantics { contentDescription = rememberLabel })
        }
        HorizontalDivider()
        val expansion = stringResource(if (showAdvanced) R.string.signal_expanded else R.string.signal_collapsed)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { stateDescription = expansion }
            .echoClickable(role = Role.Button, onClick = { showAdvanced = !showAdvanced }),
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.volume_advanced), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            EchoIcon(if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
        }
        EchoExpand(showAdvanced) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SignalReadout(stringResource(R.string.volume_combined), when {
                    !enabled -> stringResource(R.string.bitperfect_bypass)
                    volume.muted -> stringResource(R.string.volume_muted)
                    else -> formatEqGain(volume.combinedGainDb(replayGain))
                })
                DspValueSlider(stringResource(R.string.volume_limit), volume.limitDb, -24f..30f, enabled,
                    ::formatEqGain, { onVolume(volume.copy(limitDb = it)) })
                SignalNote(stringResource(R.string.volume_limit_detail))
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
                SignalNote(stringResource(R.string.volume_scope))
                SignalNote(stringResource(R.string.volume_device_memory_detail))
                TextButton(onClick = { onVolume(volume.copy(levelDb = 0f, gainDb = 0f,
                    limitDb = 30f, stepDb = 0.5f, muted = false)) }, enabled = enabled,
                    contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.volume_reset)) }
            }
        }
    }
}
