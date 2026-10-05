package app.echo.android.feature.listening

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** Reserve a chat viewport even with large text, a short window or the keyboard open. */
@Composable
internal fun ListeningRoomLayout(
    modifier: Modifier = Modifier,
    summary: @Composable () -> Unit,
    chat: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val gap = with(density) { 16.dp.roundToPx() }
    val minimumSplitWidth = with(density) { (720.dp * fontScale.coerceAtLeast(1f)).roundToPx() }
    val minimumSplitHeight = with(density) { (200.dp * fontScale.coerceAtLeast(1f)).roundToPx() }
    Layout(modifier = modifier, content = {
        Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) { summary() }
        Box(Modifier.fillMaxSize()) { chat() }
    }) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val split = width >= minimumSplitWidth && height >= minimumSplitHeight
        val spacing = gap.coerceAtMost(minOf(width, height))
        val summaryWidth = if (split) ((width - spacing) * 0.42f).toInt() else width
        val summaryHeight = if (split) height else ((height - spacing) * 0.55f).toInt()
        val first = measurables[0].measure(Constraints(
            minWidth = summaryWidth, maxWidth = summaryWidth,
            minHeight = if (split) height else 0, maxHeight = summaryHeight,
        ))
        val chatWidth = if (split) width - summaryWidth - spacing else width
        val chatHeight = if (split) height else height - first.height - spacing
        val second = measurables[1].measure(Constraints.fixed(chatWidth, chatHeight))
        layout(width, height) {
            first.placeRelative(0, 0)
            second.placeRelative(if (split) summaryWidth + spacing else 0,
                if (split) 0 else first.height + spacing)
        }
    }
}
