package app.echo.android.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Header text and buttons must avoid the book-mode hinge too. */
@Composable
internal fun HingeSafePlayerHeader(fold: PlayerFold?, content: @Composable () -> Unit) {
    if (fold == null || fold.horizontal) { content(); return }
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().onGloballyPositioned {
        val position = it.positionInWindow()
        origin = IntOffset(position.x.roundToInt(), position.y.roundToInt())
    }) {
        val bounds = with(density) {
            playerPanePlacement(maxWidth.roundToPx(), 1, 18.dp.roundToPx(), fold, origin.x, origin.y,
                preferSplit = false, allowSplit = false).cover
        }
        Box(Modifier.offset { IntOffset(bounds.x, 0) }.width(with(density) { bounds.width.toDp() })) { content() }
    }
}
