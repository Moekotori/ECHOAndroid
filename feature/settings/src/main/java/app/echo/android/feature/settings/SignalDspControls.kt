package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun DspChoices(labels: List<String>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().background(colors.surfaceContainerHigh, RoundedCornerShape(10.dp)).padding(3.dp).selectableGroup()) {
        labels.forEachIndexed { index, label ->
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(8.dp), color = if (index == selected) colors.surface else colors.surfaceContainerHigh) {
                Box(Modifier.heightIn(min = 44.dp).selectable(index == selected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(index) }).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (!enabled) colors.onSurface.copy(alpha = 0.38f) else if (index == selected) colors.primary else colors.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DspValueSlider(label: String, value: Float, valueRange: ClosedFloatingPointRange<Float>, enabled: Boolean, format: (Float) -> String, onCommit: (Float) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    val colors = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Text(format(draft), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = if (enabled) colors.primary else colors.onSurfaceVariant)
        }
        Slider(value = draft, onValueChange = { draft = it }, onValueChangeFinished = { onCommit(draft) }, valueRange = valueRange, enabled = enabled,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            thumb = { Box(Modifier.size(16.dp).background(if (enabled) colors.primary else colors.outline, CircleShape)) },
            track = { slider -> SliderDefaults.Track(slider, Modifier.height(4.dp), thumbTrackGapSize = 0.dp, drawStopIndicator = null) })
    }
}
