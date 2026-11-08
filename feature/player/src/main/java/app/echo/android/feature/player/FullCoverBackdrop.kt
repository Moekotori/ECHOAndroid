package app.echo.android.feature.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.design.LocalEchoEffectivePerformanceMode

/**
 * The cover itself, edge to edge behind the whole player. A top veil keeps the status bar readable, the
 * lower half fades into [tint] for the title and controls, and the lyrics page dims it by [reveal].
 * The image is one request at hero size; the dim is applied while drawing, so swiping never recomposes it.
 */
@Composable
internal fun FullCoverBackdrop(
    artworkUri: String?,
    tint: Color,
    reveal: () -> Float,
    modifier: Modifier = Modifier,
) {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    Crossfade(
        targetState = artworkUri,
        modifier = modifier.fillMaxSize().drawWithCache {
            val veil = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.38f),
                0.2f to Color.Transparent,
                0.42f to Color.Transparent,
                0.74f to tint.copy(alpha = 0.92f),
                1f to tint,
            )
            onDrawWithContent {
                drawContent()
                drawRect(veil)
                val lyrics = reveal().coerceIn(0f, 1f)
                if (lyrics > 0f) drawRect(Color.Black.copy(alpha = LyricsDim * lyrics))
            }
        },
        animationSpec = if (lightweight) snap() else tween(420),
        label = "full-cover-artwork",
    ) { uri ->
        EchoArtworkImage(
            artworkUri = uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            sizeClass = EchoArtworkSize.Hero,
        )
    }
}

private const val LyricsDim = 0.66f
