package app.echo.android.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.roundToInt

/** One indicator moves between tabs, including when a transition is interrupted. */
@Composable
internal fun LibraryTextTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    compact: Boolean = false,
    pager: PagerState? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val widths = remember(labels, density) { mutableStateMapOf<Int, Int>() }
    val spacing = if (compact) 24.dp else 18.dp
    val indicatorWidth = if (compact) 16.dp else 18.dp
    val spacingPx = with(density) { spacing.toPx() }
    val scroll = rememberScrollState()
    LaunchedEffect(selectedIndex, labels, density, compact, lightweight) {
        snapshotFlow {
            val center = libraryTabIndicatorCenterPx(widths, selectedIndex, labels.size)
            val width = widths[selectedIndex]
            if (center == null || width == null || scroll.viewportSize <= 0) null
            else Triple(center + spacingPx * selectedIndex - width / 2f, width, scroll.viewportSize)
        }.filterNotNull().distinctUntilChanged().collect { (left, width, viewport) ->
            val target = libraryTabRevealScroll(scroll.value, viewport, scroll.maxValue, left, left + width)
            if (target != scroll.value) {
                if (lightweight) scroll.scrollTo(target)
                else scroll.animateScrollTo(target, tween(160, easing = EchoMotion.Silk))
            }
        }
    }
    Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val color by animateColorAsState(
                    if (selected) scheme.primary else scheme.onSurfaceVariant,
                    animationSpec = if (lightweight) snap() else tween(100),
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
        val centerPx = libraryTabIndicatorCenterPx(widths, selectedIndex, labels.size)
        if (centerPx != null) {
            val target = with(density) { centerPx.toDp() } + spacing * selectedIndex - indicatorWidth / 2
            // Pager motion is already animated. Read its position only during placement,
            // without a second animation or per-frame recomposition of the labels/list.
            val position = if (pager == null) animateDpAsState(target,
                animationSpec = if (lightweight) snap() else tween(160, easing = EchoMotion.Silk),
                label = "library-tab-indicator") else null
            Box(Modifier.align(Alignment.BottomStart).padding(bottom = 4.dp)
                .offset {
                    val x = if (pager == null) position!!.value.roundToPx() else {
                        val center = libraryTabInterpolatedCenterPx(
                            widths, pager.currentPage + pager.currentPageOffsetFraction, labels.size, spacingPx,
                        ) ?: (centerPx + spacingPx * selectedIndex)
                        (center - indicatorWidth.toPx() / 2f).roundToInt()
                    }
                    IntOffset(x, 0)
                }
                .width(indicatorWidth).height(3.dp).background(scheme.primary, CircleShape))
        }
    }
}

internal fun libraryTabInterpolatedCenterPx(
    widths: Map<Int, Int>,
    pagePosition: Float,
    tabCount: Int,
    spacingPx: Float,
): Float? {
    if (tabCount <= 0 || !pagePosition.isFinite()) return null
    val page = pagePosition.coerceIn(0f, (tabCount - 1).toFloat())
    val index = page.toInt()
    val start = libraryTabIndicatorCenterPx(widths, index, tabCount)?.plus(spacingPx * index) ?: return null
    val fraction = page - index
    if (fraction == 0f) return start
    val end = libraryTabIndicatorCenterPx(widths, index + 1, tabCount)?.plus(spacingPx * (index + 1)) ?: return null
    return start + (end - start) * fraction
}

internal fun libraryTabRevealScroll(current: Int, viewport: Int, maxScroll: Int, left: Float, right: Float): Int {
    val target = when {
        right - left > viewport || left < current -> left
        right > current + viewport -> right - viewport
        else -> current.toFloat()
    }
    return target.roundToInt().coerceIn(0, maxScroll)
}

internal fun libraryTabIndicatorCenterPx(widths: Map<Int, Int>, selectedIndex: Int, tabCount: Int): Float? {
    // indexOf returns -1 while a restored category is absent from the source's tabs.
    // An empty 0..-1 range passes all(), so validate the index before reading widths.
    if (selectedIndex !in 0 until tabCount) return null
    var left = 0L
    for (index in 0 until selectedIndex) left += widths[index] ?: return null
    val width = widths[selectedIndex] ?: return null
    return left + width / 2f
}
