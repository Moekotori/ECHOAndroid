package app.echo.android.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Tabletop puts lyrics above the fold and the existing touch controls below it. */
@Composable
internal fun AdaptivePlayerPanes(
    fold: PlayerFold?,
    modifier: Modifier = Modifier,
    preferSplit: Boolean = true,
    allowSplit: Boolean = true,
    onSplitChanged: (Boolean) -> Unit = {},
    cover: @Composable () -> Unit,
    lyrics: @Composable () -> Unit,
    singlePage: @Composable () -> Unit,
) {
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(modifier.onGloballyPositioned {
            val offset = it.positionInWindow()
            origin = IntOffset(offset.x.roundToInt(), offset.y.roundToInt())
        }) {
    val panes = with(density) {
        playerPanePlacement(maxWidth.roundToPx(), maxHeight.roundToPx(), 18.dp.roundToPx(), fold, origin.x, origin.y,
            preferSplit = preferSplit, allowSplit = allowSplit,
            minimumPaneWidth = ((if (fold?.horizontal == true) 480.dp else 280.dp) * fontScale.coerceAtLeast(1f)).roundToPx(),
            minimumPaneHeight = (180.dp * fontScale.coerceAtLeast(1f)).roundToPx(), rtl = rtl)
    }
    SideEffect { onSplitChanged(panes.isSplit) }
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            Box(Modifier.fillMaxSize()) { if (panes.isSplit) cover() else singlePage() }
            if (panes.isSplit) Box(Modifier.fillMaxSize()) { lyrics() }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val first = measurables[0].measure(Constraints.fixed(panes.cover.width, panes.cover.height))
        val lyricsBounds = panes.lyrics
        val second = lyricsBounds?.let { measurables[1].measure(Constraints.fixed(it.width, it.height)) }
        layout(width, height) {
            first.place(panes.cover.x, panes.cover.y)
            if (second != null && lyricsBounds != null) second.place(lyricsBounds.x, lyricsBounds.y)
        }
    }
    }
}
