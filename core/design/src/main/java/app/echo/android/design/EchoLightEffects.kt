package app.echo.android.design

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The existing click source supplies position/cancellation; this never intercepts gestures. */
fun Modifier.echoEdgeLight(
    interactionSource: MutableInteractionSource,
    color: Color,
    cornerRadius: Dp,
    drawEdge: Boolean = true,
): Modifier = composed {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    // Do not install collectors, animation state or gradient caches in the reduced mode.
    if (lightweight) return@composed Modifier
    val pressed by interactionSource.collectIsPressedAsState()
    var pressPosition by remember(interactionSource) { mutableStateOf(Offset.Unspecified) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) pressPosition = interaction.pressPosition
        }
    }
    val light = animateFloatAsState(
        if (pressed) 1f else 0f,
        tween(if (pressed) 100 else 340, easing = EchoMotion.Silk),
        label = "edge-light",
    )
    drawWithCache {
        val strokeWidth = 1.dp.toPx()
        val inset = strokeWidth / 2f
        val edge = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.48f), color.copy(alpha = 0.12f), color.copy(alpha = 0.40f)),
            end = Offset(size.width, size.height),
        )
        val stroke = Stroke(strokeWidth)
        val radius = CornerRadius((cornerRadius.toPx() - inset).coerceAtLeast(0f))
        val bounds = Size((size.width - strokeWidth).coerceAtLeast(0f), (size.height - strokeWidth).coerceAtLeast(0f))
        val clip = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(cornerRadius.toPx()))) }
        val spotRadius = minOf(size.minDimension * 0.9f, 88.dp.toPx()).coerceAtLeast(1f)
        val spot = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.08f), color.copy(alpha = 0.06f), Color.Transparent),
            center = Offset.Zero, radius = spotRadius,
        )
        onDrawWithContent {
            // Light belongs beneath the foreground: glyphs must keep their original contrast.
            if (light.value > 0f) {
                val point = pressPosition.let {
                    if (it.x.isFinite() && it.y.isFinite()) Offset(it.x.coerceIn(0f, size.width), it.y.coerceIn(0f, size.height))
                    else center
                }
                clipPath(clip) {
                    translate(point.x, point.y) { drawCircle(spot, spotRadius, Offset.Zero, alpha = light.value) }
                }
            }
            drawContent()
            if (drawEdge && light.value > 0f) {
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
    // Balanced keeps the artwork wash without scheduling continuous redraws.
    val drift: State<Float> = if (mode.isHighPerformance) rememberInfiniteTransition(label = "ambient-light").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(EchoMotion.LightDriftMs, easing = EchoMotion.SilkExit),
            RepeatMode.Reverse,
        ),
        label = "ambient-drift",
    ) else rememberUpdatedState(0f)
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
