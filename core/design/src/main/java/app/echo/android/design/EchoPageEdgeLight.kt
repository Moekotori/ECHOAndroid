package app.echo.android.design

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * A soft light that catches the leading (left) edge of a page while it slides.
 * [light] is read in the draw phase only, so the page is never recomposed by the animation.
 */
internal fun Modifier.echoPageEdgeLight(
    light: () -> Float,
    tint: Color,
    dark: Boolean,
): Modifier = drawWithCache {
    val glowWidth = 40.dp.toPx()
    val lineWidth = 1.dp.toPx()
    val glow = Brush.horizontalGradient(
        listOf(
            (if (dark) Color.White else tint).copy(alpha = if (dark) 0.10f else 0.07f),
            tint.copy(alpha = 0.03f),
            Color.Transparent,
        ),
        startX = 0f,
        endX = glowWidth,
    )
    val line = (if (dark) Color.White else tint).copy(alpha = if (dark) 0.30f else 0.22f)
    onDrawWithContent {
        drawContent()
        val strength = light().coerceIn(0f, 1f)
        if (strength > 0.002f) {
            drawRect(glow, size = Size(glowWidth, size.height), alpha = strength)
            drawRect(line, size = Size(lineWidth, size.height), alpha = strength)
        }
    }
}

/** A thin rim around a page being dragged away; it grows with the back gesture. */
internal fun Modifier.echoPageRimLight(
    progress: () -> Float,
    cornerDp: Float,
    dark: Boolean,
): Modifier = drawWithCache {
    val width = 1.dp.toPx()
    val rim = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.34f else 0.50f),
            Color.White.copy(alpha = if (dark) 0.06f else 0.12f),
        ),
    )
    onDrawWithContent {
        drawContent()
        val strength = progress().coerceIn(0f, 1f)
        if (strength > 0.002f) {
            val radius = CornerRadius((cornerDp * strength).dp.toPx())
            drawRoundRect(
                rim,
                topLeft = Offset(width / 2f, width / 2f),
                size = Size((size.width - width).coerceAtLeast(0f), (size.height - width).coerceAtLeast(0f)),
                cornerRadius = radius,
                alpha = strength,
                style = Stroke(width),
            )
        }
    }
}
