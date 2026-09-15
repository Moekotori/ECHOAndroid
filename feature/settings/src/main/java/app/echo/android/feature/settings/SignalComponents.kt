package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.echo.android.design.echoTheme

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
    val theme = echoTheme()
    val shape = RoundedCornerShape(20.dp)
    val pill = scheme.primary.copy(alpha = if (theme.dark) 0.22f else 0.16f)
    val progressState = rememberUpdatedState(selectedProgress)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (theme.dark) Color.Black.copy(alpha = 0.28f) else scheme.surfaceVariant.copy(alpha = 0.48f))
            .border(
                width = 1.dp,
                color = if (theme.dark) theme.glassBorder else scheme.outlineVariant.copy(alpha = 0.70f),
                shape = shape,
            )
            .padding(4.dp),
    ) {
        val tabCount = labels.size.coerceAtLeast(1)
        val tabWidthPx = with(LocalDensity.current) { maxWidth.toPx() } / tabCount
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(1f / tabCount)
                    .graphicsLayer {
                        val progress = progressState.value().coerceIn(0f, (tabCount - 1).toFloat())
                        translationX = progress * tabWidthPx
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(pill),
            )
        }
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                        .padding(horizontal = 10.dp, vertical = 10.dp),
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
    }
}
