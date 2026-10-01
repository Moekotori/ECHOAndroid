package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.*

@Composable
internal fun SignalReplayGainControls(
    status: EchoPlaybackStatus, scan: EchoReplayGainScanState, enabled: Boolean,
    onReplayGain: (Boolean, Float) -> Unit, onMode: (EchoReplayGainMode) -> Unit, onScan: () -> Unit,
) {
    val modes = EchoReplayGainMode.entries
    DspChoices(listOf(stringResource(R.string.dsp_auto), stringResource(R.string.dsp_track), stringResource(R.string.dsp_album)),
        modes.indexOf(status.replayGainMode), enabled) { onMode(modes[it]) }
    SignalReadout(stringResource(R.string.signal_loudness_tag),
        status.replayGainTrackGainDb?.let(::formatEqGain) ?: stringResource(R.string.signal_no_loudness_tag))
    DspValueSlider(stringResource(R.string.eq_preamp), status.replayGainPreampDb.coerceIn(-12f, 6f), -12f..6f,
        enabled, ::formatEqGain, { onReplayGain(status.replayGainEnabled, it) })
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onScan, enabled = status.track != null && scan != EchoReplayGainScanState.Scanning,
            contentPadding = PaddingValues(horizontal = 0.dp)) { Text(stringResource(R.string.dsp_scan_short)) }
    }
    when (scan) {
        EchoReplayGainScanState.Scanning -> {
            Text(stringResource(R.string.signal_analyzing_loudness), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        is EchoReplayGainScanState.Written -> SignalNote(stringResource(R.string.dsp_scanned, scan.gainDb))
        is EchoReplayGainScanState.Failed -> SignalNote(stringResource(when (scan.reason) {
            EchoReplayGainScanFailure.NotLocal -> R.string.signal_scan_local_only
            EchoReplayGainScanFailure.Unsupported -> R.string.signal_scan_unsupported
            EchoReplayGainScanFailure.DecodeFailed -> R.string.signal_scan_decode_failed
            EchoReplayGainScanFailure.WriteFailed -> R.string.signal_scan_write_failed
        }), error = true)
        else -> if (status.track == null) SignalNote(stringResource(R.string.signal_scan_select_track))
    }
}
