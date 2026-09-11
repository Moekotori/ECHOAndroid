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
import androidx.compose.runtime.Composable
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
    val fadersEnabled = state.enabled && !bypassed
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
                        state.processingSampleRateHz == null && live -> L10nR.string.channel_balance_waiting
                        live -> L10nR.string.channel_balance_processing
                        else -> L10nR.string.channel_balance_disabled
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
                    Text(
                        formatBalanceBias(state.balance),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary,
                    )
                    Text(stringResource(L10nR.string.channel_balance_right), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
                SignalGainStrip(
                    value = state.balance,
                    valueRange = EchoChannelBalance.MinBalance..EchoChannelBalance.MaxBalance,
                    enabled = fadersEnabled,
                    contentDescription = title,
                    onValueChange = { onStateChange(state.copy(balance = it)) },
                    snap = { (it * 100f).roundToInt() / 100f },
                )
            }
        }

        SignalEqWell(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChannelGainRow(
                    label = stringResource(L10nR.string.channel_balance_left_gain),
                    value = state.leftGainDb,
                    enabled = fadersEnabled,
                    onValueChange = { onStateChange(state.copy(leftGainDb = it)) },
                )
                ChannelGainRow(
                    label = stringResource(L10nR.string.channel_balance_right_gain),
                    value = state.rightGainDb,
                    enabled = fadersEnabled,
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
                    onClick = { onStateChange(state.copy(swapLeftRight = !state.swapLeftRight)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(L10nR.string.channel_balance_mono), style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChannelMonoOption(EchoChannelBalanceMonoMode.Off, L10nR.string.channel_balance_mono_off, state, onStateChange, Modifier.weight(1f))
                    ChannelMonoOption(EchoChannelBalanceMonoMode.Sum, L10nR.string.channel_balance_mono_sum, state, onStateChange, Modifier.weight(1f))
                    ChannelMonoOption(EchoChannelBalanceMonoMode.Left, L10nR.string.channel_balance_mono_left, state, onStateChange, Modifier.weight(1f))
                    ChannelMonoOption(EchoChannelBalanceMonoMode.Right, L10nR.string.channel_balance_mono_right, state, onStateChange, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    EchoTextButton(text = stringResource(L10nR.string.feature_settings_reset_1106f5), onClick = onReset)
                }
            }
        }
    }
}

@Composable
private fun ChannelGainRow(
    label: String,
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(formatEqGain(value), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
        }
        SignalGainStrip(
            value = value,
            valueRange = EchoChannelBalance.MinGainDb..EchoChannelBalance.MaxGainDb,
            enabled = enabled,
            contentDescription = label,
            onValueChange = onValueChange,
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
) {
    ChannelToggleChip(
        label = stringResource(labelRes),
        selected = state.monoMode == mode,
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
                    Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                } else {
                    Modifier.selectable(selected = selected, role = Role.Switch, onClick = onClick)
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
private fun formatBalanceBias(balance: Float): String {
    val percent = (abs(balance) * 100f).roundToInt()
    return when {
        percent == 0 -> stringResource(L10nR.string.channel_balance_center)
        balance < 0f -> stringResource(L10nR.string.channel_balance_bias, stringResource(L10nR.string.channel_balance_left), percent)
        else -> stringResource(L10nR.string.channel_balance_bias, stringResource(L10nR.string.channel_balance_right), percent)
    }
}
