package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoExpand
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoSwitch
import app.echo.android.design.echoClickable
import app.echo.android.model.playback.EchoDspSettings
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoReplayGainMode
import app.echo.android.model.playback.EchoReplayGainScanState
import app.echo.android.model.playback.EchoVolumeSettings

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
    onOpenUsb: () -> Unit,
) {
    val enabled = !status.diagnostics.usbVolumeLocked
    val canProcess = !status.diagnostics.usbBitPerfectEnabled
    val outputLabel = deviceLabel ?: stringResource(R.string.volume_current_output)
    Column {
        if (enabled) {
            // Discard a draft or open editor when the output route changes.
            key(volume.deviceKey, deviceLabel) {
                SignalVolumeHero(volume, outputLabel, onVolume)
            }
        } else {
            SettingsInfoRow(outputLabel, stringResource(R.string.volume_bypassed))
        }
        if (status.diagnostics.usbConnected || !enabled) {
            HorizontalDivider()
            SettingsActionRow(
                title = stringResource(R.string.usb_fine_title),
                detail = stringResource(R.string.volume_usb_shortcut_note),
                onClick = onOpenUsb,
            )
        }
        SignalVolumeDisclosure(
            title = stringResource(R.string.volume_gain_section),
            summary = formatEqGain(volume.gainDb),
        ) {
            key(volume.deviceKey, deviceLabel, enabled) {
                VolumeGainControls(volume, status, dsp.limiterEnabled, enabled, canProcess, onVolume)
            }
        }
        SignalVolumeDisclosure(title = stringResource(R.string.volume_processing_section)) {
            VolumeProcessingControls(status, dsp, scan, canProcess, onDsp, onReplayGain, onMode, onScan)
        }
        SignalVolumeDisclosure(
            title = stringResource(R.string.volume_device_section),
            summary = stringResource(if (volume.rememberPerDevice) R.string.volume_device_saved else R.string.volume_shared),
        ) {
            VolumeDeviceControls(volume, enabled, deviceLabel, onVolume)
        }
        HorizontalDivider()
    }
}

@Composable
private fun VolumeGainControls(
    volume: EchoVolumeSettings,
    status: EchoPlaybackStatus,
    limiterEnabled: Boolean,
    enabled: Boolean,
    canProcess: Boolean,
    onVolume: (EchoVolumeSettings) -> Unit,
) {
    DspValueSlider(stringResource(R.string.volume_gain), volume.gainDb, -24f..12f, enabled,
        ::formatEqGain, { onVolume(volume.copy(gainDb = it)) })
    TextButton(
        onClick = { onVolume(volume.copy(gainDb = 0f)) },
        enabled = enabled && volume.gainDb != 0f,
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(stringResource(R.string.volume_gain_zero))
    }
    if (enabled && volume.gainDb > 0f && !limiterEnabled) {
        SignalNote(stringResource(R.string.volume_gain_hint))
    }
    DspValueSlider(stringResource(R.string.volume_limit), volume.limitDb, -24f..30f, enabled,
        ::formatEqGain, { onVolume(volume.copy(limitDb = it)) })
    SignalNote(stringResource(R.string.volume_limit_detail))
    val replayGain = if (canProcess && status.replayGainEnabled) {
        status.replayGainPreampDb + (status.replayGainTrackGainDb ?: 0f)
    } else 0f
    SignalReadout(stringResource(R.string.volume_combined), when {
        !enabled -> stringResource(R.string.bitperfect_bypass)
        volume.muted -> stringResource(R.string.volume_muted)
        else -> formatEqGain(volume.combinedGainDb(replayGain))
    })
}

@Composable
private fun VolumeProcessingControls(
    status: EchoPlaybackStatus,
    dsp: EchoDspSettings,
    scan: EchoReplayGainScanState,
    enabled: Boolean,
    onDsp: (EchoDspSettings) -> Unit,
    onReplayGain: (Boolean, Float) -> Unit,
    onMode: (EchoReplayGainMode) -> Unit,
    onScan: () -> Unit,
) {
    var replayGainExpanded by rememberSaveable { mutableStateOf(status.replayGainEnabled) }
    var limiterExpanded by rememberSaveable { mutableStateOf(false) }
    if (!enabled) SignalNote(stringResource(R.string.signal_bypass_detail))
    val replayGainMode = stringResource(when (status.replayGainMode) {
        EchoReplayGainMode.Auto -> R.string.dsp_auto
        EchoReplayGainMode.Track -> R.string.dsp_track
        EchoReplayGainMode.Album -> R.string.dsp_album
    })
    SignalDspSection(
        title = stringResource(R.string.dsp_loudness),
        detail = stringResource(R.string.dsp_loudness_detail),
        summary = "$replayGainMode · ${formatEqGain(status.replayGainPreampDb)}",
        checked = status.replayGainEnabled,
        enabled = enabled,
        expanded = replayGainExpanded,
        onExpand = { replayGainExpanded = !replayGainExpanded },
        onChecked = {
            onReplayGain(it, status.replayGainPreampDb)
            if (it) replayGainExpanded = true
        },
        onReset = {
            onReplayGain(false, 0f)
            onMode(EchoReplayGainMode.Auto)
        },
    ) {
        SignalReplayGainControls(status, scan, enabled, onReplayGain, onMode, onScan)
    }
    SignalEqualLoudnessControl(dsp, enabled, onDsp)
    HorizontalDivider()
    SignalCompressorControl(dsp, enabled, onDsp)
    SignalDspSection(
        title = stringResource(R.string.dsp_limiter),
        detail = stringResource(R.string.dsp_limiter_detail),
        summary = "${signalNumber(dsp.limiterCeilingDb)} dBFS",
        checked = dsp.limiterEnabled,
        enabled = enabled,
        expanded = limiterExpanded,
        onExpand = { limiterExpanded = !limiterExpanded },
        onChecked = { onDsp(dsp.copy(limiterEnabled = it)) },
        onReset = { onDsp(dsp.copy(limiterEnabled = false, limiterCeilingDb = -1f)) },
    ) {
        DspValueSlider(stringResource(R.string.dsp_output_ceiling), dsp.limiterCeilingDb, -6f..-0.1f, enabled,
            { "${signalNumber(it)} dBFS" }, { onDsp(dsp.copy(limiterCeilingDb = it)) }, unit = "dBFS")
    }
}

@Composable
private fun VolumeDeviceControls(
    volume: EchoVolumeSettings,
    enabled: Boolean,
    deviceLabel: String?,
    onVolume: (EchoVolumeSettings) -> Unit,
) {
    var confirming by rememberSaveable(volume.deviceKey, deviceLabel, enabled) { mutableStateOf(false) }
    val rememberLabel = stringResource(R.string.volume_remember_per_device)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(rememberLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        EchoSwitch(
            volume.rememberPerDevice,
            { onVolume(volume.copy(rememberPerDevice = it)) },
            modifier = Modifier.semantics { contentDescription = rememberLabel },
        )
    }
    SignalNote(stringResource(R.string.volume_scope))
    SignalNote(stringResource(R.string.volume_device_memory_detail))
    TextButton(
        onClick = { confirming = true },
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(stringResource(R.string.volume_reset))
    }
    if (confirming && enabled) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.volume_reset)) },
            text = { Text(stringResource(R.string.volume_reset_confirm_detail)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    val defaults = EchoVolumeSettings()
                    onVolume(volume.copy(levelDb = defaults.levelDb, gainDb = defaults.gainDb,
                        limitDb = defaults.limitDb, stepDb = defaults.stepDb, muted = defaults.muted))
                }) { Text(stringResource(R.string.volume_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.usb_reset_cancel)) }
            },
        )
    }
}

@Composable
private fun SignalVolumeDisclosure(
    title: String,
    summary: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val expansion = stringResource(if (expanded) R.string.signal_expanded else R.string.signal_collapsed)
    HorizontalDivider()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp)
            .semantics { stateDescription = expansion }
            .echoClickable(role = Role.Button, onClick = { expanded = !expanded })
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (summary != null) SignalNote(summary)
        }
        EchoIcon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(20.dp))
    }
    EchoExpand(expanded) {
        Column(Modifier.padding(top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content)
    }
}
