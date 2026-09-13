package app.echo.android.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkPalette
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoTheme

/** Local, finite light treatment: no extra artwork request, blur texture or idle ticker. */
@Composable
internal fun NowPlayingArtworkLight(
    palette: ArtworkPalette,
    expanded: Boolean,
    gestureStrength: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val theme = echoTheme()
    val presence = remember { Animatable(if (lightweight) 1f else 0.55f) }
    LaunchedEffect(expanded, lightweight) {
        if (lightweight) presence.snapTo(1f)
        else presence.animateTo(
            if (expanded) 1f else 0f,
            tween(if (expanded) 460 else 300, easing = EchoMotion.Silk),
        )
    }
    val glow = lerp(palette.vibrant, theme.accent, if (dark) 0.18f else 0.35f)
    val softGlow = lerp(palette.soft, theme.accent, 0.30f)
    Box(
        modifier = modifier.drawWithCache {
            val center = Offset(size.width * 0.5f, size.height * 0.52f)
            val radius = size.minDimension * 0.78f
            val halo = Brush.radialGradient(
                0f to Color.Transparent,
                0.44f to glow.copy(alpha = if (dark) 0.26f else 0.12f),
                0.66f to glow.copy(alpha = if (dark) 0.13f else 0.055f),
                1f to Color.Transparent,
                center = center,
                radius = radius.coerceAtLeast(1f),
            )
            val lowerCenter = Offset(size.width * 0.48f, size.height * 0.88f)
            val lowerRadius = size.minDimension * 0.54f
            val lowerHalo = Brush.radialGradient(
                listOf(softGlow.copy(alpha = if (dark) 0.14f else 0.045f), Color.Transparent),
                center = lowerCenter,
                radius = lowerRadius.coerceAtLeast(1f),
            )
            val rim = Brush.linearGradient(
                0f to Color.White.copy(alpha = if (dark) 0.38f else 0.76f),
                0.34f to Color.White.copy(alpha = if (dark) 0.09f else 0.20f),
                0.66f to Color.Transparent,
                1f to glow.copy(alpha = if (dark) 0.22f else 0.14f),
            )
            val lineWidth = 0.75.dp.toPx()
            val inset = lineWidth / 2f
            val rimSize = Size((size.width - lineWidth).coerceAtLeast(0f), (size.height - lineWidth).coerceAtLeast(0f))
            val corner = CornerRadius((24.dp.toPx() - inset).coerceAtLeast(0f))
            val stroke = Stroke(lineWidth)
            onDrawWithContent {
                // Read changing values in the draw phase; cached brushes and image composition stay intact.
                val strength = if (lightweight) 0.5f else presence.value * gestureStrength().coerceIn(0f, 1f)
                drawCircle(halo, radius, center, alpha = strength)
                if (!lightweight) drawCircle(lowerHalo, lowerRadius, lowerCenter, alpha = strength)
                drawContent()
                drawRoundRect(rim, Offset(inset, inset), rimSize, corner, alpha = if (lightweight) 0.5f else 0.65f + 0.35f * strength, style = stroke)
            }
        },
        content = content,
    )
}
