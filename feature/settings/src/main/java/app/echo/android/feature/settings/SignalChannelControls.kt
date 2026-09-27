package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import app.echo.android.design.EchoSwitch
import app.echo.android.model.playback.EchoChannelBalanceMonoMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChannelValueSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    valueLabel: String = formatEqGain(value),
    startLabel: String = formatEqGain(valueRange.start),
    endLabel: String = formatEqGain(valueRange.endInclusive),
    showReadout: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (enabled) scheme.primary else scheme.onSurfaceVariant
    Column {
        if (showReadout) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = tint)
        }
        Slider(
            value = value.coerceIn(valueRange), valueRange = valueRange, enabled = enabled,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label; stateDescription = valueLabel },
            thumb = { Box(Modifier.size(18.dp).background(tint, CircleShape).border(3.dp, scheme.surface, CircleShape)) },
            track = { slider ->
                SliderDefaults.Track(slider, Modifier.height(4.dp).drawBehind {
                    // Mark neutral rather than suggesting that the range midpoint is 0 dB.
                    val fraction = -valueRange.start / (valueRange.endInclusive - valueRange.start)
                    if (fraction > 0f && fraction < 1f) {
                        val x = size.width * if (layoutDirection == LayoutDirection.Rtl) 1f - fraction else fraction
                        drawLine(scheme.onSurfaceVariant.copy(alpha = 0.5f), Offset(x, -4.dp.toPx()),
                            Offset(x, size.height + 4.dp.toPx()), 1.dp.toPx())
                    }
                }, thumbTrackGapSize = 0.dp, drawStopIndicator = null)
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(startLabel, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text(endLabel, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ChannelToggleRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, detail: String? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(10.dp))
        .toggleable(selected, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
        .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
            if (detail != null) SignalNote(detail)
        }
        EchoSwitch(checked = selected, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
internal fun ChannelMonoChoices(selected: EchoChannelBalanceMonoMode, enabled: Boolean, onSelect: (EchoChannelBalanceMonoMode) -> Unit) {
    val modes = listOf(
        EchoChannelBalanceMonoMode.Off to R.string.channel_balance_mono_off,
        EchoChannelBalanceMonoMode.Sum to R.string.channel_balance_mono_sum,
        EchoChannelBalanceMonoMode.Left to R.string.channel_balance_mono_left,
        EchoChannelBalanceMonoMode.Right to R.string.channel_balance_mono_right,
    )
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        modes.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (mode, label) ->
                    val picked = selected == mode
                    val shape = RoundedCornerShape(4.dp)
                    Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(shape)
                        .background(if (picked) scheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                        .selectable(picked, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(mode) })
                        .padding(10.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                            fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                            color = (if (picked) scheme.primary else scheme.onSurfaceVariant).copy(alpha = if (enabled) 1f else 0.38f))
                    }
                }
            }
        }
    }
}
