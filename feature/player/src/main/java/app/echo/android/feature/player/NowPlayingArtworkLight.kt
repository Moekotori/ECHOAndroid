package app.echo.android.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/** A finite cover rim. Keep lighting inside the cover; outward halos form clipped haze. */
@Composable
internal fun NowPlayingArtworkLight(
    palette: ArtworkPalette,
    expanded: Boolean,
    gestureStrength: () -> Float,
    modifier: Modifier = Modifier,
    /** Idle / missing cover must not draw a decorative rim. */
    enabled: Boolean = true,
    trackKey: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val theme = echoTheme()
    val presence = remember { Animatable(if (!enabled) 0f else if (lightweight) 1f else 0.55f) }
    val handoff = remember { Animatable(1f) }
    LaunchedEffect(trackKey, lightweight, enabled) {
        if (lightweight || !enabled) handoff.snapTo(1f)
        else {
            handoff.snapTo(0.78f)
            handoff.animateTo(1f, tween(EchoMotion.LightHandoffMs, easing = EchoMotion.Silk))
        }
    }
    LaunchedEffect(expanded, lightweight, enabled) {
        if (!enabled) presence.snapTo(0f)
        else if (lightweight) presence.snapTo(1f)
        else presence.animateTo(
            if (expanded) 1f else 0f,
            EchoMotion.silkFloat(if (expanded) 460 else 300),
        )
    }
    if (!enabled) {
        Box(modifier = modifier, content = content)
        return
    }
    // Palette changes stay inside this light layer; the cover request is unchanged.
    val glow by animateColorAsState(
        lerp(palette.vibrant, theme.accent, if (dark) 0.18f else 0.35f),
        tween(if (lightweight) 0 else EchoMotion.LightHandoffMs), label = "cover-light-color",
    )
    Box(
        modifier = modifier.drawWithCache {
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
                val strength = if (lightweight) 0.5f else presence.value * handoff.value * gestureStrength().coerceIn(0f, 1f)
                drawContent()
                drawRoundRect(rim, Offset(inset, inset), rimSize, corner, alpha = if (lightweight) 0.5f else 0.65f + 0.35f * strength, style = stroke)
            }
        },
        content = content,
    )
}
