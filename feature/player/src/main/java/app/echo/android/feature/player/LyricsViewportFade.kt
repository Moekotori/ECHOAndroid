package app.echo.android.feature.player

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** The mask is cached until the viewport changes. Lightweight mode avoids the extra layer. */
internal fun Modifier.lyricsViewportFade(enabled: Boolean): Modifier = if (!enabled) this else
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithCache {
        val edge = (28.dp.toPx() / size.height.coerceAtLeast(1f)).coerceAtMost(0.20f)
        val mask = Brush.verticalGradient(
            0f to Color.Transparent, edge to Color.Black,
            1f - edge to Color.Black, 1f to Color.Transparent,
        )
        onDrawWithContent {
            drawContent()
            drawRect(mask, blendMode = BlendMode.DstIn)
        }
    }
