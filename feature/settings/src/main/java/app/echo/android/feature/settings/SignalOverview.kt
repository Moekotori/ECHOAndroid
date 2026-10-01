package app.echo.android.feature.settings

import app.echo.android.feature.settings.R as L10nR

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.*

@Composable
internal fun SignalOverview(
    status: EchoPlaybackStatus,
    equalizer: EchoEqualizerState,
    channelBalance: EchoChannelBalanceState = EchoChannelBalanceState(),
    dspSettings: EchoDspSettings = EchoDspSettings(),
    onAdjust: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    val d = status.diagnostics
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SignalDeviceCard(status)
        SignalPathPanel(status, equalizer, channelBalance, dspSettings)
        SignalDacPanel(status, onDiagnostics)
        SignalSection(stringResource(L10nR.string.feature_settings_output_details_f242d2)) {
            SignalReadout(
                stringResource(L10nR.string.diag_decoded_output),
                if (d.isDsdSource()) {
                    d.decodedFormatLabel()
                } else {
                    (d.decodedSampleRateHz ?: d.sampleRateHz)?.takeIf { it > 0 }?.let(::formatSampleRate)
                        ?: stringResource(L10nR.string.diag_unreported)
                },
            )
            SignalReadout(stringResource(L10nR.string.diag_bitrate), d.bitrate?.let(::formatBitrate) ?: stringResource(L10nR.string.diag_unreported))
            SignalReadout("Bit-perfect", d.bitPerfectReadout(equalizer))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onAdjust) { Text(stringResource(L10nR.string.feature_settings_adjust_sound_f8940e)) }
                TextButton(onClick = onDiagnostics) { Text(stringResource(L10nR.string.feature_settings_view_diagnostics_31fcec)) }
            }
        }
    }
}
