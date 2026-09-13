package app.echo.android.ui.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode

/** Read animation and predictive-back progress in the layer, not the page composition. */
@Composable
internal fun Modifier.echoPlayerDepth(
    expanded: Boolean,
    backProgress: () -> Float,
): Modifier {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val depth = animateFloatAsState(
        targetValue = if (expanded && !lightweight) 1f else 0f,
        animationSpec = if (lightweight) tween(0) else EchoMotion.silkFloat(520),
        label = "player-background-depth",
    )
    return graphicsLayer {
        val progress = depth.value * (1f - backProgress().coerceIn(0f, 1f))
        transformOrigin = TransformOrigin(0.5f, 0.12f)
        scaleX = 1f - 0.035f * progress
        scaleY = scaleX
        translationY = 8.dp.toPx() * progress
        // Keep the resting page out of an offscreen alpha layer.
        alpha = 1f - 0.12f * progress
        shape = RoundedCornerShape((24f * progress).dp)
        clip = progress > 0f
    }
}
