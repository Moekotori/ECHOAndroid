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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoExpand
import app.echo.android.design.EchoSwitch
import app.echo.android.model.playback.*
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun SignalChannelBalance(
    state: EchoChannelBalanceState,
    bypassed: Boolean,
    playing: Boolean,
    onStateChange: (EchoChannelBalanceState) -> Unit,
    onReset: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val title = stringResource(R.string.channel_balance)
    val live = state.enabled && !bypassed && playing && state.affectsSignal && (state.processingSampleRateHz ?: 0) > 0
    val controlsEnabled = !bypassed
    var showAdvanced by rememberSaveable { mutableStateOf(state.hasAdvancedSettings) }
    val effective = remember(state.balance, state.leftGainDb, state.rightGainDb, state.constantPower) {
        FloatArray(2).also {
            EchoChannelBalance.writeBalanceGains(state.balance, state.leftGainDb, state.rightGainDb, it, state.constantPower)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    SignalLiveDot(active = live)
                    Text(stringResource(when {
                        bypassed -> R.string.eq_bypassed
                        !state.enabled -> R.string.channel_balance_disabled
                        !playing -> R.string.channel_balance_waiting
                        !state.affectsSignal -> R.string.channel_balance_idle
                        !live -> R.string.channel_balance_waiting
                        else -> R.string.channel_balance_processing
                    }), style = MaterialTheme.typography.bodySmall,
                        color = if (live) scheme.primary else scheme.onSurfaceVariant)
                }
            }
            EchoSwitch(checked = state.enabled, enabled = controlsEnabled,
                onCheckedChange = { onStateChange(state.copy(enabled = it)) },
                modifier = Modifier.semantics { contentDescription = title })
        }
        if (!state.enabled && !bypassed) SignalNote(stringResource(R.string.channel_edit_while_off))
        if (live) SignalNote(stringResource(R.string.channel_processing_rate, formatSampleRate(state.processingSampleRateHz!!)))

        SignalSection(stringResource(R.string.channel_pan_title), stringResource(R.string.channel_pan_hint)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatBalanceBias(state.balance), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { onStateChange(state.copy(balance = 0f)) },
                    enabled = controlsEnabled && state.balance != 0f) {
                    Text(stringResource(R.string.channel_balance_center))
                }
            }
            ChannelValueSlider(
                label = stringResource(R.string.channel_pan_title), value = state.balance,
                valueRange = EchoChannelBalance.MinBalance..EchoChannelBalance.MaxBalance,
                enabled = controlsEnabled, valueLabel = formatBalanceBias(state.balance),
                onValueChange = { onStateChange(state.copy(balance = (it * 100f).roundToInt() / 100f)) },
                startLabel = stringResource(R.string.channel_balance_left), endLabel = stringResource(R.string.channel_balance_right),
                showReadout = false,
            )
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            Text(stringResource(R.string.channel_trim_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            // Stack narrow layouts and large type so gain labels and sliders retain usable width.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = maxWidth < 320.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f
                val left: @Composable (Modifier) -> Unit = { modifier ->
                    ChannelLevelCard(stringResource(R.string.channel_balance_left_gain), state.leftGainDb,
                        formatOutputGain(effective[0]), controlsEnabled,
                        { onStateChange(state.copy(leftGainDb = it)) }, modifier)
                }
                val right: @Composable (Modifier) -> Unit = { modifier ->
                    ChannelLevelCard(stringResource(R.string.channel_balance_right_gain), state.rightGainDb,
                        formatOutputGain(effective[1]), controlsEnabled,
                        { onStateChange(state.copy(rightGainDb = it)) }, modifier)
                }
                if (stacked) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    left(Modifier.fillMaxWidth())
                    right(Modifier.fillMaxWidth())
                } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    left(Modifier.weight(1f))
                    right(Modifier.weight(1f))
                }
            }
            SignalNote(stringResource(R.string.channel_gain_estimate_note))
            if (state.clippingRisk) SignalNote(stringResource(R.string.channel_balance_clipping), error = true)
        }

        SignalSection(stringResource(R.string.channel_routing_title), stringResource(R.string.channel_routing_hint)) {
            ChannelToggleRow(stringResource(R.string.channel_balance_swap), state.swapLeftRight, controlsEnabled,
                onClick = { onStateChange(state.copy(swapLeftRight = !state.swapLeftRight)) },
                detail = stringResource(R.string.channel_swap_hint))
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            ChannelMonoChoices(state.monoMode, controlsEnabled) { onStateChange(state.copy(monoMode = it)) }
            SignalNote(stringResource(when (state.monoMode) {
                EchoChannelBalanceMonoMode.Off -> R.string.channel_stereo_hint
                EchoChannelBalanceMonoMode.Sum -> R.string.channel_sum_hint
                EchoChannelBalanceMonoMode.Left -> R.string.channel_left_only_hint
                EchoChannelBalanceMonoMode.Right -> R.string.channel_right_only_hint
            }))
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(R.string.channel_advanced_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(if (state.hasAdvancedSettings) R.string.channel_advanced_custom else R.string.channel_advanced_hint),
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Icon(if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(if (showAdvanced) R.string.channel_balance_hide_advanced else R.string.channel_balance_show_advanced))
            }
            EchoExpand(showAdvanced) {
                ChannelAdvancedControls(state, controlsEnabled, onStateChange)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onReset, enabled = controlsEnabled) { Text(stringResource(R.string.channel_reset_all)) }
        }
    }
}

@Composable
private fun formatOutputGain(linear: Float): String =
    if (linear < 0.001f) stringResource(R.string.channel_muted) else formatEqGain(EchoChannelBalance.linearToDb(linear))

@Composable
private fun formatBalanceBias(balance: Float): String {
    val percent = (abs(balance) * 100f).roundToInt()
    return when {
        percent == 0 -> stringResource(R.string.channel_balance_center)
        balance < 0f -> stringResource(R.string.channel_balance_bias, stringResource(R.string.channel_balance_left), percent)
        else -> stringResource(R.string.channel_balance_bias, stringResource(R.string.channel_balance_right), percent)
    }
}
