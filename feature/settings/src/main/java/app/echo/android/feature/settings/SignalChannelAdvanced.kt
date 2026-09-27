package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoChannelBalance
import app.echo.android.model.playback.EchoChannelBalanceState
import kotlin.math.abs
import kotlin.math.roundToInt

internal val EchoChannelBalanceState.hasAdvancedSettings: Boolean
    get() = !constantPower || invertLeft || invertRight ||
        leftBandGainsDb.any { abs(it) >= EchoChannelBalance.GainEpsilonDb } ||
        rightBandGainsDb.any { abs(it) >= EchoChannelBalance.GainEpsilonDb } ||
        leftDelayMs >= EchoChannelBalance.DelayEpsilonMs || rightDelayMs >= EchoChannelBalance.DelayEpsilonMs

@Composable
internal fun ChannelAdvancedControls(state: EchoChannelBalanceState, enabled: Boolean, onChange: (EchoChannelBalanceState) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ChannelAdvancedHeading(stringResource(R.string.channel_pan_law), stringResource(R.string.channel_pan_law_hint))
        ChannelToggleRow(stringResource(R.string.channel_balance_constant_power), state.constantPower, enabled,
            { onChange(state.copy(constantPower = !state.constantPower)) },
            stringResource(if (state.constantPower) R.string.channel_balance_constant_power_on else R.string.channel_balance_constant_power_off))

        ChannelAdvancedHeading(stringResource(R.string.channel_polarity_title), stringResource(R.string.channel_polarity_hint))
        Column {
            ChannelToggleRow(stringResource(R.string.channel_balance_invert_left), state.invertLeft, enabled,
                { onChange(state.copy(invertLeft = !state.invertLeft)) })
            ChannelToggleRow(stringResource(R.string.channel_balance_invert_right), state.invertRight, enabled,
                { onChange(state.copy(invertRight = !state.invertRight)) })
        }

        ChannelAdvancedHeading(stringResource(R.string.channel_balance_bands), stringResource(R.string.channel_tone_hint))
        ChannelBandControls(R.string.channel_balance_band_low, R.string.channel_balance_band_low_range, 0, state, enabled, onChange)
        ChannelBandControls(R.string.channel_balance_band_mid, R.string.channel_balance_band_mid_range, 1, state, enabled, onChange)
        ChannelBandControls(R.string.channel_balance_band_high, R.string.channel_balance_band_high_range, 2, state, enabled, onChange)

        ChannelAdvancedHeading(stringResource(R.string.channel_delay_title), stringResource(R.string.channel_delay_hint))
        ChannelValueSlider(stringResource(R.string.channel_balance_delay_left), state.leftDelayMs,
            EchoChannelBalance.MinDelayMs..EchoChannelBalance.MaxDelayMs, enabled,
            { onChange(state.copy(leftDelayMs = (it * 10f).roundToInt() / 10f)) },
            valueLabel = stringResource(R.string.channel_balance_delay_ms, state.leftDelayMs),
            startLabel = stringResource(R.string.channel_balance_delay_ms, EchoChannelBalance.MinDelayMs),
            endLabel = stringResource(R.string.channel_balance_delay_ms, EchoChannelBalance.MaxDelayMs))
        ChannelValueSlider(stringResource(R.string.channel_balance_delay_right), state.rightDelayMs,
            EchoChannelBalance.MinDelayMs..EchoChannelBalance.MaxDelayMs, enabled,
            { onChange(state.copy(rightDelayMs = (it * 10f).roundToInt() / 10f)) },
            valueLabel = stringResource(R.string.channel_balance_delay_ms, state.rightDelayMs),
            startLabel = stringResource(R.string.channel_balance_delay_ms, EchoChannelBalance.MinDelayMs),
            endLabel = stringResource(R.string.channel_balance_delay_ms, EchoChannelBalance.MaxDelayMs))
    }
}

@Composable
private fun ChannelAdvancedHeading(title: String, hint: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider(Modifier.padding(bottom = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        SignalNote(hint)
    }
}

@Composable
private fun ChannelBandControls(titleRes: Int, rangeRes: Int, index: Int, state: EchoChannelBalanceState,
    enabled: Boolean, onChange: (EchoChannelBalanceState) -> Unit,
) {
    val left = remember(state.leftBandGainsDb) { EchoChannelBalance.resizeBands(state.leftBandGainsDb) }
    val right = remember(state.rightBandGainsDb) { EchoChannelBalance.resizeBands(state.rightBandGainsDb) }
    val band = stringResource(titleRes)
    val leftLabel = stringResource(R.string.channel_balance_left)
    val rightLabel = stringResource(R.string.channel_balance_right)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(band, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(rangeRes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ChannelValueSlider("$leftLabel · $band", left[index], EchoChannelBalance.MinBandGainDb..EchoChannelBalance.MaxBandGainDb,
            enabled, { onChange(state.copy(leftBandGainsDb = left.toMutableList().also { bands -> bands[index] = snapEqGain(it) })) })
        ChannelValueSlider("$rightLabel · $band", right[index], EchoChannelBalance.MinBandGainDb..EchoChannelBalance.MaxBandGainDb,
            enabled, { onChange(state.copy(rightBandGainsDb = right.toMutableList().also { bands -> bands[index] = snapEqGain(it) })) })
    }
}
