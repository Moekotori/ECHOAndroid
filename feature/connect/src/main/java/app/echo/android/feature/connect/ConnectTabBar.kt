package app.echo.android.feature.connect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val TrackShape = RoundedCornerShape(16.dp)
private val ThumbShape = RoundedCornerShape(12.dp)

/** Pill segmented control. The thumb follows the pager in the layout phase, so swiping never recomposes it. */
@Composable
internal fun ConnectTabBar(
    labels: List<String>,
    pagerState: PagerState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    Surface(modifier.fillMaxWidth(), shape = TrackShape, color = scheme.surfaceContainer) {
        BoxWithConstraints(Modifier.padding(4.dp)) {
            val tabWidth = maxWidth / labels.size
            Box(
                Modifier
                    .offset { IntOffset(((pagerState.currentPage + pagerState.currentPageOffsetFraction) * tabWidth.toPx()).roundToInt(), 0) }
                    .width(tabWidth).height(40.dp)
                    .background(if (dark) scheme.surfaceContainerHighest else scheme.surfaceContainerLowest, ThumbShape),
            )
            Row(Modifier.fillMaxWidth().selectableGroup()) {
                labels.forEachIndexed { index, label ->
                    val selected = pagerState.currentPage == index
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(ThumbShape)
                            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
