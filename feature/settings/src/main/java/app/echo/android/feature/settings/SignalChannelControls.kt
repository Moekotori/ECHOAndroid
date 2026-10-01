package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoSlider
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
    unit: String = "dB",
    scale: Float = 1f,
    decimals: Int = 1,
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        if (showReadout) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            SignalNumericValue(label, value, valueRange, enabled, onValueChange,
                valueLabel = valueLabel, unit = unit, scale = scale, decimals = decimals)
        }
        EchoSlider(
            value = value.coerceIn(valueRange), valueRange = valueRange, enabled = enabled,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label; stateDescription = valueLabel },
            neutralValue = 0f,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(startLabel, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            if (valueRange.start == -valueRange.endInclusive) Text("0", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
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
    val modes = EchoChannelBalanceMonoMode.entries
    DspChoices(
        labels = listOf(
            stringResource(R.string.signal_stereo), stringResource(R.string.signal_mono_sum),
            stringResource(R.string.signal_left_only), stringResource(R.string.signal_right_only),
        ),
        selected = modes.indexOf(selected), enabled = enabled,
        onSelect = { onSelect(modes[it]) },
    )
}
