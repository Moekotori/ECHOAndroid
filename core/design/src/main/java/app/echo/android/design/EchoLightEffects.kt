package app.echo.android.design

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Share the control's interaction source, including cancelled presses and keyboard focus. */
fun Modifier.echoEdgeLight(
    interactionSource: MutableInteractionSource,
    color: Color,
    cornerRadius: Dp,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val light = animateFloatAsState(
        if (pressed && !lightweight) 1f else 0f,
        tween(if (lightweight) 0 else if (pressed) 100 else 340, easing = EchoMotion.Silk),
        label = "edge-light",
    )
    drawWithCache {
        val strokeWidth = 1.dp.toPx()
        val inset = strokeWidth / 2f
        val edge = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.75f), color.copy(alpha = 0.18f), color.copy(alpha = 0.65f)),
            end = Offset(size.width, size.height),
        )
        val stroke = Stroke(strokeWidth)
        val radius = CornerRadius((cornerRadius.toPx() - inset).coerceAtLeast(0f))
        val bounds = Size((size.width - strokeWidth).coerceAtLeast(0f), (size.height - strokeWidth).coerceAtLeast(0f))
        onDrawWithContent {
            drawContent()
            if (light.value > 0f) {
                drawRoundRect(edge, Offset(inset, inset), bounds, radius, alpha = light.value, style = stroke)
            }
        }
    }
}

/** Caller supplies page/lifecycle visibility. Only two cached gradients move; no blur or images. */
@Composable
fun EchoAmbientLight(
    color: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
    strength: () -> Float = { 1f },
) {
    val mode = LocalEchoEffectivePerformanceMode.current
    if (!active || mode.isLightweight || !LocalWindowInfo.current.isWindowFocused) return
    val dark = LocalEchoDarkTheme.current
    val drift = rememberInfiniteTransition(label = "ambient-light").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(EchoMotion.LightDriftMs, easing = EchoMotion.SilkExit),
            RepeatMode.Reverse,
        ),
        label = "ambient-drift",
    )
    Crossfade(
        targetState = color,
        modifier = modifier.clipToBounds(),
        animationSpec = tween(EchoMotion.LightHandoffMs),
        label = "ambient-palette",
    ) { tint ->
        Box(Modifier.fillMaxSize().drawWithCache {
            val radius = size.width * 0.85f
            val center = Offset(size.width * 0.12f, size.height * 0.25f)
            val secondCenter = Offset(size.width * 0.96f, size.height * 0.48f)
            val glow = Brush.radialGradient(
                listOf(tint.copy(alpha = if (dark) 0.14f else 0.055f), Color.Transparent),
                center, radius.coerceAtLeast(1f),
            )
            val secondGlow = Brush.radialGradient(
                listOf(tint.copy(alpha = if (dark) 0.09f else 0.035f), Color.Transparent),
                secondCenter, radius.coerceAtLeast(1f),
            )
            val travel = size.width * if (mode.isHighPerformance) 0.16f else 0.10f
            onDrawBehind {
                val displacement = drift.value * travel
                val alpha = strength().coerceIn(0f, 1f)
                translate(left = displacement, top = -displacement * 0.4f) {
                    drawCircle(glow, radius, center, alpha = alpha)
                }
                translate(left = -displacement, top = displacement * 0.5f) {
                    drawCircle(secondGlow, radius, secondCenter, alpha = alpha)
                }
            }
        })
    }
}
