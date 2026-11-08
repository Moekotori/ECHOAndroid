package app.echo.android.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoMotion
import app.echo.android.model.playback.EchoTrackRef
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Follow the finger directly; animate only pickup and the final handoff. */
@Composable
internal fun QueueDragOverlay(
    track: EchoTrackRef,
    source: QueueDragTarget,
    rowHeight: Int,
    top: () -> Float,
    released: Boolean,
    settlement: Int,
    destinationTop: Float,
    lightweight: Boolean,
    onSettled: () -> Unit,
) {
    val lift = remember { Animatable(0f) }
    val landing = remember(settlement) { Animatable(0f) }
    val settled by rememberUpdatedState(onSettled)
    val dragShape = remember { RoundedCornerShape(14.dp) }
    // Changing performance mode must not restart a completed landing and resend the edit.
    LaunchedEffect(released, settlement) {
        if (!released) {
            if (!lightweight) lift.animateTo(1f, EchoMotion.silkFloat(180))
        } else {
            coroutineScope {
                launch {
                    landing.animateTo(1f, if (lightweight) tween(90) else EchoMotion.silkFloat(240))
                }
                launch {
                    lift.animateTo(0f, if (lightweight) tween(90) else EchoMotion.silkFloat(240))
                }
            }
            settled()
        }
    }
    QueueTrackRow(
        track, source.row, source.lastRow, false, {}, {}, {}, {},
        modifier = Modifier
            .height(with(LocalDensity.current) { rowHeight.toDp() })
            .graphicsLayer {
                val fingerTop = top()
                translationY = if (released) fingerTop + (destinationTop - fingerTop) * landing.value else fingerTop
                val depth = if (lightweight) 0f else lift.value
                scaleX = 1f - 0.008f * depth
                scaleY = scaleX
                shape = dragShape
                clip = true
                shadowElevation = 6.dp.toPx() * depth
            }
            .background(queueSurfaceColor())
            .clearAndSetSemantics {},
        interactive = false,
    )
}
