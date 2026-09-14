package app.echo.android.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode

/** One indicator moves between tabs, including when a transition is interrupted. */
@Composable
internal fun LibraryTextTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val widths = remember(labels, density) { mutableStateMapOf<Int, Int>() }
    val spacing = if (compact) 24.dp else 20.dp
    val indicatorWidth = if (compact) 16.dp else 24.dp
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val color by animateColorAsState(
                    if (selected) scheme.primary else scheme.onSurfaceVariant,
                    animationSpec = tween(if (lightweight) 120 else 220),
                    label = "library-tab-color",
                )
                Column(
                    Modifier.onSizeChanged { widths[index] = it.width }
                        .selectable(selected, role = Role.Tab, onClick = { keyboard?.hide(); onSelect(index) })
                        .heightIn(min = 48.dp).padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Reserve the bold width so selecting a label cannot shift its neighbours.
                    Box(contentAlignment = Alignment.Center) {
                        Text(label, color = androidx.compose.ui.graphics.Color.Transparent,
                            modifier = Modifier.clearAndSetSemantics { },
                            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                        Text(label, color = color,
                            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        if ((0..selectedIndex).all { widths.containsKey(it) }) {
            val target = with(density) {
                ((0 until selectedIndex).sumOf { widths.getValue(it) } + widths.getValue(selectedIndex) / 2f).toDp()
            } + spacing * selectedIndex - indicatorWidth / 2
            val position = animateDpAsState(target,
                animationSpec = if (lightweight) snap() else EchoMotion.silkDp(EchoMotion.TabMs),
                label = "library-tab-indicator")
            Box(Modifier.align(Alignment.BottomStart).padding(bottom = 4.dp)
                .offset { IntOffset(position.value.roundToPx(), 0) }
                .width(indicatorWidth).height(2.dp).background(scheme.primary))
        }
    }
}
