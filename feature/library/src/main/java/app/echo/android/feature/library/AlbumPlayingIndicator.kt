package app.echo.android.feature.library

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.echo.android.design.EchoIcon
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.LocalEchoPageInteractive
import app.echo.android.design.echoIconColor
import kotlin.math.abs

/** Only this small painter redraws; animation never invalidates the track row or list. */
@Composable
internal fun AlbumPlayingIndicator(isPlaying: Boolean) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animate = isPlaying && !LocalEchoEffectivePerformanceMode.current.isLightweight &&
        LocalEchoPageInteractive.current && LocalWindowInfo.current.isWindowFocused &&
        lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val phase = if (animate) {
        rememberInfiniteTransition(label = "album-playing").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "album-playing-bars",
        )
    } else {
        rememberUpdatedState(0f)
    }
    val painter = remember(phase) { AlbumPlayingPainter(phase) }
    EchoIcon(
        painter = painter,
        contentDescription = null,
        tint = echoIconColor(),
        modifier = Modifier.size(16.dp),
    )
}

private class AlbumPlayingPainter(private val phase: State<Float>) : Painter() {
    override val intrinsicSize = Size(16f, 16f)

    override fun DrawScope.onDraw() {
        val unit = size.minDimension / 16f
        val bottom = size.height / 2f + 6f * unit
        val left = size.width / 2f - 6f * unit
        val frame = phase.value
        for (bar in 0..3) {
            val progress = (frame + bar / 4f) % 1f
            val bounce = FastOutSlowInEasing.transform(1f - abs(progress * 2f - 1f))
            val height = (3f + 9f * bounce) * unit
            val x = left + bar * 4f * unit
            drawLine(
                color = Color.White,
                start = Offset(x, bottom),
                end = Offset(x, bottom - height),
                strokeWidth = 2f * unit,
                cap = StrokeCap.Round,
            )
        }
    }
}
