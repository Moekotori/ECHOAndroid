package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Signal sections use whitespace and one rule; nested information never gets its own card. */
@Composable
internal fun SignalSection(
    title: String,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (!subtitle.isNullOrBlank()) SignalNote(subtitle)
            }
            action?.invoke()
        }
        content()
    }
}

@Composable
internal fun SignalNote(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Normal,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun SignalReadout(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, modifier = Modifier.weight(0.36f), style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer(Modifier.weight(0.64f)) {
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
internal fun SignalMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
        SignalNote(label)
    }
}

@Composable
internal fun SignalSoundModeRow(
    selectedIndex: Int,
    labels: List<String>,
    onSelect: (Int) -> Unit,
    selectedProgress: () -> Float = { selectedIndex.toFloat() },
) {
    val scheme = MaterialTheme.colorScheme
    val progressState = rememberUpdatedState(selectedProgress)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tabCount = labels.size.coerceAtLeast(1)
        val tabWidthPx = with(LocalDensity.current) { maxWidth.toPx() } / tabCount
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().selectableGroup()) {
                labels.forEachIndexed { index, label ->
                    val selected = index == selectedIndex
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(2.dp)) {
                Box(Modifier.fillMaxSize().background(scheme.outlineVariant.copy(alpha = 0.55f)))
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .fillMaxWidth(1f / tabCount)
                        .graphicsLayer {
                            val progress = progressState.value().coerceIn(0f, (tabCount - 1).toFloat())
                            translationX = progress * tabWidthPx
                        }
                        .background(scheme.primary),
                )
            }
        }
    }
}
