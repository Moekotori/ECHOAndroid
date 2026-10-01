package app.echo.android.feature.settings

import app.echo.android.design.EchoSlider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import kotlin.math.round

@Composable
internal fun DspChoices(labels: List<String>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { index, label ->
            val picked = index == selected
            Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(6.dp))
                .background(if (picked) colors.primary.copy(alpha = 0.09f) else colors.surfaceContainerLow)
                .selectable(picked, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(index) })
                .padding(horizontal = 6.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (!enabled) colors.onSurface.copy(alpha = 0.38f) else if (picked) colors.primary else colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun DspValueSlider(
    label: String, value: Float, valueRange: ClosedFloatingPointRange<Float>, enabled: Boolean,
    format: (Float) -> String, onCommit: (Float) -> Unit,
    unit: String = "dB", scale: Float = 1f, decimals: Int = 1,
) {
    var draft by remember(value) { mutableFloatStateOf(value.coerceIn(valueRange)) }
    val commit = rememberUpdatedState(onCommit)
    val colors = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            SignalNumericValue(label, draft, valueRange, enabled, {
                draft = it
                commit.value(it)
            }, valueLabel = format(draft), unit = unit, scale = scale, decimals = decimals)
        }
        EchoSlider(value = draft, onValueChange = {
            val factor = 10f.pow(decimals) * scale
            draft = (round(it * factor) / factor).coerceIn(valueRange)
        }, onValueChangeFinished = { if (enabled) commit.value(draft) }, valueRange = valueRange, enabled = enabled,
            neutralValue = 0f.takeIf { it in valueRange },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label; stateDescription = format(draft) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(format(valueRange.start), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text(format(valueRange.endInclusive), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}
