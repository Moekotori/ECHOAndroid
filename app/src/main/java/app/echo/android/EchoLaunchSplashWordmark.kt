package app.echo.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

private const val Letters = "ECHO"

/**
 * ECHO lettering: letters rise in one by one, then the word repeats outward as a faint outline
 * (the echo) while a highlight sweeps across it. Everything is driven by the splash clock.
 */
@Composable
internal fun EchoLaunchWordmark(
    fx: EchoLaunchSplashFx,
    palette: LaunchSplashPalette,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val fontSize = with(density) { 30.dp.toSp() }
    val gap = 30.dp * 0.52f
    val strokeWidth = with(density) { 1.dp.toPx() }
    val base = remember(palette, fontFamily, fontSize) {
        TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = fontSize, color = palette.ink)
    }
    val firstEcho = remember(palette, base, strokeWidth) {
        base.copy(color = palette.accent, drawStyle = Stroke(width = strokeWidth))
    }
    val secondEcho = remember(palette, base, strokeWidth) {
        base.copy(color = palette.accent3, drawStyle = Stroke(width = strokeWidth))
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        EchoGhost(fx, firstEcho, gap, LaunchSplashTiming.EchoStart)
        EchoGhost(fx, secondEcho, gap, LaunchSplashTiming.EchoStart + 0.25f)
        Row(
            Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawShine(fx.time.floatValue)
                },
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            Letters.forEachIndexed { index, letter ->
                BasicText(
                    text = letter.toString(),
                    style = base,
                    modifier = Modifier.graphicsLayer {
                        val start = LaunchSplashTiming.LetterStart + index * LaunchSplashTiming.LetterStagger
                        val p = LetterEasing.transform(launchSplashProgress(fx.time.floatValue, start, LaunchSplashTiming.LetterDuration))
                        alpha = p
                        translationY = 14.dp.toPx() * (1f - p)
                        val s = 0.94f + 0.06f * p
                        scaleX = s
                        scaleY = s
                    },
                )
            }
        }
    }
}

@Composable
private fun EchoGhost(fx: EchoLaunchSplashFx, style: TextStyle, gap: Dp, delay: Float) {
    Row(
        Modifier.graphicsLayer {
            val cycle = (fx.time.floatValue - delay) / LaunchSplashTiming.EchoCycle
            if (cycle < 0f) {
                alpha = 0f
                return@graphicsLayer
            }
            val q = fractionOf(cycle)
            alpha = when {
                q < 0.12f -> q / 0.12f * 0.55f
                q < 0.55f -> 0.55f * (1f - (q - 0.12f) / 0.43f)
                else -> 0f
            }
            val s = 1f + 0.34f * EchoEasing.transform(min(q / 0.55f, 1f))
            scaleX = s
            scaleY = s
        },
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        Letters.forEach { BasicText(it.toString(), style = style) }
    }
}

/** A soft white band passing left to right over the lettering, once per echo cycle. */
private fun DrawScope.drawShine(time: Float) {
    val cycle = (time - LaunchSplashTiming.EchoStart) / LaunchSplashTiming.EchoCycle
    if (cycle < 0f) return
    val move = EaseInOut.transform(min(fractionOf(cycle) / 0.46f, 1f))
    if (move >= 1f) return
    val center = size.width * (-0.3f + 1.6f * move)
    val half = size.width * 0.36f
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.5f to Color.White.copy(alpha = 0.95f),
            1f to Color.Transparent,
            startX = center - half,
            endX = center + half,
        ),
        blendMode = BlendMode.SrcAtop,
    )
}
