package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    var page by rememberSaveable { mutableIntStateOf(when {
        state.leftDelayMs > 0f || state.rightDelayMs > 0f -> 3
        state.leftBandGainsDb.any { it != 0f } || state.rightBandGainsDb.any { it != 0f } -> 2
        state.invertLeft || state.invertRight -> 1
        else -> 0
    }) }
    val title = stringResource(when (page) {
        0 -> R.string.channel_pan_law
        1 -> R.string.channel_polarity_title
        2 -> R.string.channel_balance_bands
        else -> R.string.channel_delay_title
    })
    val hint = stringResource(when (page) {
        0 -> R.string.channel_pan_law_hint
        1 -> R.string.channel_polarity_hint
        2 -> R.string.channel_tone_hint
        else -> R.string.channel_delay_hint
    })
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DspChoices(listOf(stringResource(R.string.signal_pan_law_short), stringResource(R.string.signal_polarity_short),
            stringResource(R.string.signal_tone_short), stringResource(R.string.signal_delay_short)), page, true) { page = it }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            SignalHelpButton(title, hint)
            TextButton(enabled = enabled, onClick = {
                onChange(when (page) {
                    0 -> state.copy(constantPower = true)
                    1 -> state.copy(invertLeft = false, invertRight = false)
                    2 -> state.copy(leftBandGainsDb = EchoChannelBalance.zeroBands, rightBandGainsDb = EchoChannelBalance.zeroBands)
                    else -> state.copy(leftDelayMs = 0f, rightDelayMs = 0f)
                })
            }) { Text(stringResource(R.string.feature_settings_reset_1106f5)) }
        }
        when (page) {
            0 -> {
                DspChoices(listOf(stringResource(R.string.signal_constant_power), stringResource(R.string.signal_linear_pan)),
                    if (state.constantPower) 0 else 1, enabled) { onChange(state.copy(constantPower = it == 0)) }
                SignalNote(stringResource(if (state.constantPower) R.string.signal_constant_power_note else R.string.channel_balance_constant_power_off))
            }
            1 -> {
                ChannelToggleRow(stringResource(R.string.channel_balance_invert_left), state.invertLeft, enabled,
                    { onChange(state.copy(invertLeft = !state.invertLeft)) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ChannelToggleRow(stringResource(R.string.channel_balance_invert_right), state.invertRight, enabled,
                    { onChange(state.copy(invertRight = !state.invertRight)) })
            }
            2 -> ChannelToneControls(state, enabled, onChange)
            3 -> {
                ChannelValueSlider(stringResource(R.string.channel_balance_delay_left), state.leftDelayMs,
                    EchoChannelBalance.MinDelayMs..EchoChannelBalance.MaxDelayMs, enabled,
                    { onChange(state.copy(leftDelayMs = (it * 10f).roundToInt() / 10f)) },
                    valueLabel = "${signalNumber(state.leftDelayMs)} ms", startLabel = "0 ms", endLabel = "10 ms", unit = "ms")
                ChannelValueSlider(stringResource(R.string.channel_balance_delay_right), state.rightDelayMs,
                    EchoChannelBalance.MinDelayMs..EchoChannelBalance.MaxDelayMs, enabled,
                    { onChange(state.copy(rightDelayMs = (it * 10f).roundToInt() / 10f)) },
                    valueLabel = "${signalNumber(state.rightDelayMs)} ms", startLabel = "0 ms", endLabel = "10 ms", unit = "ms")
            }
        }
    }
}

@Composable
private fun ChannelToneControls(state: EchoChannelBalanceState, enabled: Boolean, onChange: (EchoChannelBalanceState) -> Unit) {
    var band by rememberSaveable { mutableIntStateOf(0) }
    val left = remember(state.leftBandGainsDb) { EchoChannelBalance.resizeBands(state.leftBandGainsDb) }
    val right = remember(state.rightBandGainsDb) { EchoChannelBalance.resizeBands(state.rightBandGainsDb) }
    val labels = listOf(stringResource(R.string.channel_balance_band_low), stringResource(R.string.channel_balance_band_mid), stringResource(R.string.channel_balance_band_high))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DspChoices(labels, band, true) { band = it }
        Text(stringResource(when (band) {
            0 -> R.string.channel_balance_band_low_range
            1 -> R.string.channel_balance_band_mid_range
            else -> R.string.channel_balance_band_high_range
        }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChannelValueSlider("${stringResource(R.string.channel_balance_left)} · ${labels[band]}", left[band],
            EchoChannelBalance.MinBandGainDb..EchoChannelBalance.MaxBandGainDb, enabled,
            { onChange(state.copy(leftBandGainsDb = left.toMutableList().also { bands -> bands[band] = snapEqGain(it) })) })
        ChannelValueSlider("${stringResource(R.string.channel_balance_right)} · ${labels[band]}", right[band],
            EchoChannelBalance.MinBandGainDb..EchoChannelBalance.MaxBandGainDb, enabled,
            { onChange(state.copy(rightBandGainsDb = right.toMutableList().also { bands -> bands[band] = snapEqGain(it) })) })
    }
}
