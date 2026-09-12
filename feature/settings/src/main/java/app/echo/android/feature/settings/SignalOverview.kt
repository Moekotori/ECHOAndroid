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
    onAdjust: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    val d = status.diagnostics
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SignalDeviceCard(status)
        SignalPathPanel(status, equalizer, channelBalance)
        SignalSection(stringResource(L10nR.string.feature_settings_output_details_f242d2)) {
            SignalReadout(
                stringResource(L10nR.string.diag_decoded_output),
                if (d.isDsdSource()) {
                    d.decodedFormatLabel()
                } else {
                    d.decodedSampleRateHz?.let(::formatSampleRate) ?: stringResource(L10nR.string.diag_unreported)
                },
            )
            SignalReadout(stringResource(L10nR.string.diag_bitrate), d.bitrate?.let(::formatBitrate) ?: stringResource(L10nR.string.diag_unreported))
            SignalReadout("Bit-perfect", d.bitPerfectReadout(equalizer))
            if (d.usbBitPerfectEnabled && d.bitPerfectOutputBits != null) {
                SignalReadout(stringResource(L10nR.string.bitperfect_precision), stringResource(L10nR.string.bitperfect_precision_value,
                    d.bitPerfectSourceBits ?: 0, d.bitPerfectDecodedBits ?: 0, d.bitPerfectOutputBits ?: 0))
                SignalReadout(stringResource(L10nR.string.bitperfect_clock_label), d.bitPerfectSampleRateHz?.let(::formatSampleRate)
                    ?: stringResource(L10nR.string.diag_unreported))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onAdjust) { Text(stringResource(L10nR.string.feature_settings_adjust_sound_f8940e)) }
                TextButton(onClick = onDiagnostics) { Text(stringResource(L10nR.string.feature_settings_view_diagnostics_31fcec)) }
            }
        }
    }
}
