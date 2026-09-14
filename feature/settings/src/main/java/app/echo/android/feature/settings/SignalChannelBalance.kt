package app.echo.android.feature.settings

import app.echo.android.feature.settings.R as L10nR

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.foundation.selection.toggleable
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
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(shape = RoundedCornerShape(20.dp), color = scheme.primary.copy(alpha = 0.10f)) {
                    Icon(Icons.Default.Headphones, contentDescription = null,
                        tint = scheme.primary, modifier = Modifier.padding(16.dp).size(32.dp))
                }
                Text(formatBalanceBias(state.balance), style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(L10nR.string.channel_balance_left), style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = state.balance,
                        onValueChange = { onStateChange(state.copy(balance = (it * 100f).roundToInt() / 100f)) },
                        valueRange = EchoChannelBalance.MinBalance..EchoChannelBalance.MaxBalance,
                        enabled = controlsEnabled,
                        modifier = Modifier.weight(1f).semantics { contentDescription = title },
                    )
                    Text(stringResource(L10nR.string.channel_balance_right), style = MaterialTheme.typography.titleSmall)
                }
                TextButton(onClick = { onStateChange(state.copy(balance = 0f)) },
                    enabled = controlsEnabled && state.balance != 0f) {
                    Text(stringResource(L10nR.string.channel_balance_center))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChannelLevelCard(
                        label = stringResource(L10nR.string.channel_balance_left_gain),
                        value = state.leftGainDb,
                        output = stringResource(L10nR.string.channel_balance_output,
                            stringResource(L10nR.string.channel_balance_left), formatOutputGain(effective[0])),
                        enabled = controlsEnabled,
                        onValueChange = { onStateChange(state.copy(leftGainDb = it)) },
                        modifier = Modifier.weight(1f),
                    )
                    ChannelLevelCard(
                        label = stringResource(L10nR.string.channel_balance_right_gain),
                        value = state.rightGainDb,
                        output = stringResource(L10nR.string.channel_balance_output,
                            stringResource(L10nR.string.channel_balance_right), formatOutputGain(effective[1])),
                        enabled = controlsEnabled,
                        onValueChange = { onStateChange(state.copy(rightGainDb = it)) },
                        modifier = Modifier.weight(1f),
                    )
                }
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
                Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChannelMonoOption(EchoChannelBalanceMonoMode.Off, L10nR.string.channel_balance_mono_off, state, onStateChange, Modifier.weight(1f), controlsEnabled)
                    ChannelMonoOption(EchoChannelBalanceMonoMode.Sum, L10nR.string.channel_balance_mono_sum, state, onStateChange, Modifier.weight(1f), controlsEnabled)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChannelMonoOption(EchoChannelBalanceMonoMode.Left, L10nR.string.channel_balance_mono_left, state, onStateChange, Modifier.weight(1f), controlsEnabled)
                    ChannelMonoOption(EchoChannelBalanceMonoMode.Right, L10nR.string.channel_balance_mono_right, state, onStateChange, Modifier.weight(1f), controlsEnabled)
                    }
                }
            }
        }

        SignalEqWell(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (showAdvanced) L10nR.string.channel_balance_hide_advanced else L10nR.string.channel_balance_show_advanced), modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    Icon(if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
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
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ChannelToggleChip(
                                label = stringResource(L10nR.string.channel_balance_invert_left),
                                selected = state.invertLeft,
                                enabled = controlsEnabled,
                                onClick = { onStateChange(state.copy(invertLeft = !state.invertLeft)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            ChannelToggleChip(
                                label = stringResource(L10nR.string.channel_balance_invert_right),
                                selected = state.invertRight,
                                enabled = controlsEnabled,
                                onClick = { onStateChange(state.copy(invertRight = !state.invertRight)) },
                                modifier = Modifier.fillMaxWidth(),
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
        Slider(
            value = value,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            onValueChange = { onValueChange(snap(it)) },
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
    if (!radio) {
        Row(
            modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                .toggleable(value = selected, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
            Switch(checked = selected, onCheckedChange = null, enabled = enabled)
        }
        return
    }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) scheme.primary.copy(alpha = 0.13f) else scheme.onSurface.copy(alpha = 0.04f))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = (if (selected) scheme.primary else scheme.onSurfaceVariant).copy(alpha = if (enabled) 1f else 0.38f),
            style = MaterialTheme.typography.bodyMedium,
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
