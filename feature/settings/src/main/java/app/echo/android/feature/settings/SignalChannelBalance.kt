package app.echo.android.feature.settings

import app.echo.android.feature.settings.R as L10nR

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoExpand
import app.echo.android.design.EchoTextButton
import app.echo.android.design.echoGlassRowBrush
import app.echo.android.model.playback.EchoChannelBalance
import app.echo.android.model.playback.EchoChannelBalanceMonoMode
import app.echo.android.model.playback.EchoChannelBalanceState
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
    val title = stringResource(L10nR.string.channel_balance)
    val live = state.enabled && !bypassed && state.affectsSignal
    val controlsEnabled = !bypassed
    var showAdvanced by rememberSaveable { mutableStateOf(state.hasAdvancedSettings) }
    val effective = remember(state.balance, state.leftGainDb, state.rightGainDb, state.constantPower) {
        val gains = FloatArray(2)
        EchoChannelBalance.writeBalanceGains(state.balance, state.leftGainDb, state.rightGainDb, gains, state.constantPower)
        gains
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SignalLiveDot(active = live && playing && state.processingSampleRateHz != null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(L10nR.string.channel_balance_detail),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(when {
                        bypassed -> L10nR.string.eq_bypassed
                        !state.enabled -> L10nR.string.channel_balance_disabled
                        !playing -> L10nR.string.channel_balance_waiting
                        !state.affectsSignal -> L10nR.string.channel_balance_idle
                        state.processingSampleRateHz == null -> L10nR.string.channel_balance_waiting
                        else -> L10nR.string.channel_balance_processing
                    }),
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        bypassed -> scheme.error
                        live && playing -> scheme.primary
                        else -> scheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = state.enabled,
                onCheckedChange = { onStateChange(state.copy(enabled = it)) },
                modifier = Modifier.semantics { contentDescription = title },
            )
        }

        SignalEqWell(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(L10nR.string.channel_balance_left), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Text(formatBalanceBias(state.balance), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                    Text(stringResource(L10nR.string.channel_balance_right), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
                SignalGainStrip(
                    value = state.balance,
                    valueRange = EchoChannelBalance.MinBalance..EchoChannelBalance.MaxBalance,
                    enabled = controlsEnabled,
                    contentDescription = title,
                    onValueChange = { onStateChange(state.copy(balance = it)) },
                    snap = { (it * 100f).roundToInt() / 100f },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(L10nR.string.channel_balance_output, stringResource(L10nR.string.channel_balance_left), formatOutputGain(effective[0])),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(L10nR.string.channel_balance_output, stringResource(L10nR.string.channel_balance_right), formatOutputGain(effective[1])),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        SignalEqWell(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChannelGainRow(
                    label = stringResource(L10nR.string.channel_balance_left_gain),
                    value = state.leftGainDb,
                    valueRange = EchoChannelBalance.MinGainDb..EchoChannelBalance.MaxGainDb,
                    enabled = controlsEnabled,
                    onValueChange = { onStateChange(state.copy(leftGainDb = it)) },
                )
                ChannelGainRow(
                    label = stringResource(L10nR.string.channel_balance_right_gain),
                    value = state.rightGainDb,
                    valueRange = EchoChannelBalance.MinGainDb..EchoChannelBalance.MaxGainDb,
                    enabled = controlsEnabled,
                    onValueChange = { onStateChange(state.copy(rightGainDb = it)) },
                )
                if (state.clippingRisk) {
                    SignalNote(stringResource(L10nR.string.channel_balance_clipping), error = true)
                }
            }
        }

        SignalEqWell(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChannelToggleChip(
                    label = stringResource(L10nR.string.channel_balance_swap),
                    selected = state.swapLeftRight,
                    enabled = controlsEnabled,
                    onClick = { onStateChange(state.copy(swapLeftRight = !state.swapLeftRight)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(L10nR.string.channel_balance_mono), style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChannelMonoOption(EchoChannelBalanceMonoMode.Off, L10nR.string.channel_balance_mono_off, state, onStateChange, Modifier.weight(1f), controlsEnabled)
                    ChannelMonoOption(EchoChannelBalanceMonoMode.Sum, L10nR.string.channel_balance_mono_sum, state, onStateChange, Modifier.weight(1f), controlsEnabled)
                    ChannelMonoOption(EchoChannelBalanceMonoMode.Left, L10nR.string.channel_balance_mono_left, state, onStateChange, Modifier.weight(1f), controlsEnabled)
                    ChannelMonoOption(EchoChannelBalanceMonoMode.Right, L10nR.string.channel_balance_mono_right, state, onStateChange, Modifier.weight(1f), controlsEnabled)
                }
            }
        }

        SignalEqWell(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(stringResource(if (showAdvanced) L10nR.string.channel_balance_hide_advanced else L10nR.string.channel_balance_show_advanced))
                }
                EchoExpand(showAdvanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        ChannelToggleChip(
                            label = stringResource(L10nR.string.channel_balance_constant_power),
                            selected = state.constantPower,
                            enabled = controlsEnabled,
                            onClick = { onStateChange(state.copy(constantPower = !state.constantPower)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SignalNote(stringResource(if (state.constantPower) L10nR.string.channel_balance_constant_power_on else L10nR.string.channel_balance_constant_power_off))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ChannelToggleChip(
                                label = stringResource(L10nR.string.channel_balance_invert_left),
                                selected = state.invertLeft,
                                enabled = controlsEnabled,
                                onClick = { onStateChange(state.copy(invertLeft = !state.invertLeft)) },
                                modifier = Modifier.weight(1f),
                            )
                            ChannelToggleChip(
                                label = stringResource(L10nR.string.channel_balance_invert_right),
                                selected = state.invertRight,
                                enabled = controlsEnabled,
                                onClick = { onStateChange(state.copy(invertRight = !state.invertRight)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(stringResource(L10nR.string.channel_balance_bands), style = MaterialTheme.typography.titleSmall)
                        SignalNote(stringResource(L10nR.string.channel_balance_bands_hint))
                        ChannelBandRow(L10nR.string.channel_balance_band_low, L10nR.string.channel_balance_band_low_range, 0, state, controlsEnabled, onStateChange)
                        ChannelBandRow(L10nR.string.channel_balance_band_mid, L10nR.string.channel_balance_band_mid_range, 1, state, controlsEnabled, onStateChange)
                        ChannelBandRow(L10nR.string.channel_balance_band_high, L10nR.string.channel_balance_band_high_range, 2, state, controlsEnabled, onStateChange)
                        ChannelGainRow(
                            label = stringResource(L10nR.string.channel_balance_delay_left),
                            value = state.leftDelayMs,
                            valueRange = EchoChannelBalance.MinDelayMs..EchoChannelBalance.MaxDelayMs,
                            enabled = controlsEnabled,
                            valueLabel = stringResource(L10nR.string.channel_balance_delay_ms, state.leftDelayMs),
                            onValueChange = { onStateChange(state.copy(leftDelayMs = it)) },
                            snap = { (it * 10f).roundToInt() / 10f },
                        )
                        ChannelGainRow(
                            label = stringResource(L10nR.string.channel_balance_delay_right),
                            value = state.rightDelayMs,
                            valueRange = EchoChannelBalance.MinDelayMs..EchoChannelBalance.MaxDelayMs,
                            enabled = controlsEnabled,
                            valueLabel = stringResource(L10nR.string.channel_balance_delay_ms, state.rightDelayMs),
                            onValueChange = { onStateChange(state.copy(rightDelayMs = it)) },
                            snap = { (it * 10f).roundToInt() / 10f },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    EchoTextButton(text = stringResource(L10nR.string.feature_settings_reset_1106f5), onClick = onReset)
                }
            }
        }
    }
}

private val EchoChannelBalanceState.hasAdvancedSettings: Boolean
    get() = !constantPower ||
        invertLeft ||
        invertRight ||
        leftBandGainsDb.any { abs(it) >= EchoChannelBalance.GainEpsilonDb } ||
        rightBandGainsDb.any { abs(it) >= EchoChannelBalance.GainEpsilonDb } ||
        leftDelayMs >= EchoChannelBalance.DelayEpsilonMs ||
        rightDelayMs >= EchoChannelBalance.DelayEpsilonMs

@Composable
private fun ChannelBandRow(
    titleRes: Int,
    rangeRes: Int,
    index: Int,
    state: EchoChannelBalanceState,
    enabled: Boolean,
    onStateChange: (EchoChannelBalanceState) -> Unit,
) {
    val left = EchoChannelBalance.resizeBands(state.leftBandGainsDb)
    val right = EchoChannelBalance.resizeBands(state.rightBandGainsDb)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(rangeRes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ChannelGainRow(
            label = stringResource(L10nR.string.channel_balance_left),
            value = left[index],
            valueRange = EchoChannelBalance.MinBandGainDb..EchoChannelBalance.MaxBandGainDb,
            enabled = enabled,
            onValueChange = {
                onStateChange(state.copy(leftBandGainsDb = left.toMutableList().also { bands -> bands[index] = it }))
            },
        )
        ChannelGainRow(
            label = stringResource(L10nR.string.channel_balance_right),
            value = right[index],
            valueRange = EchoChannelBalance.MinBandGainDb..EchoChannelBalance.MaxBandGainDb,
            enabled = enabled,
            onValueChange = {
                onStateChange(state.copy(rightBandGainsDb = right.toMutableList().also { bands -> bands[index] = it }))
            },
        )
    }
}

@Composable
private fun ChannelGainRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    valueLabel: String = formatEqGain(value),
    snap: (Float) -> Float = ::snapEqGain,
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = scheme.primary)
        }
        SignalGainStrip(
            value = value,
            valueRange = valueRange,
            enabled = enabled,
            contentDescription = label,
            onValueChange = onValueChange,
            snap = snap,
        )
    }
}

@Composable
private fun ChannelMonoOption(
    mode: EchoChannelBalanceMonoMode,
    labelRes: Int,
    state: EchoChannelBalanceState,
    onStateChange: (EchoChannelBalanceState) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ChannelToggleChip(
        label = stringResource(labelRes),
        selected = state.monoMode == mode,
        enabled = enabled,
        onClick = { onStateChange(state.copy(monoMode = mode)) },
        modifier = modifier,
        radio = true,
    )
}

@Composable
private fun ChannelToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    radio: Boolean = false,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(echoGlassRowBrush(selected))
            .then(
                if (radio) {
                    Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                } else {
                    Modifier.selectable(selected = selected, enabled = enabled, role = Role.Switch, onClick = onClick)
                },
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun formatOutputGain(linear: Float): String =
    if (linear < 0.001f) "—" else formatEqGain(EchoChannelBalance.linearToDb(linear))

@Composable
private fun formatBalanceBias(balance: Float): String {
    val percent = (abs(balance) * 100f).roundToInt()
    return when {
        percent == 0 -> stringResource(L10nR.string.channel_balance_center)
        balance < 0f -> stringResource(L10nR.string.channel_balance_bias, stringResource(L10nR.string.channel_balance_left), percent)
        else -> stringResource(L10nR.string.channel_balance_bias, stringResource(L10nR.string.channel_balance_right), percent)
    }
}
