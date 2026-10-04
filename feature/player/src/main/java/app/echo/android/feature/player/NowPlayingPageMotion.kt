package app.echo.android.feature.player

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs

/** Pager progress drives the layers directly, without clocks or per-frame composition. */
internal fun Modifier.nowPlayingPageDepth(
    offset: () -> Float,
    cover: Boolean,
    lightweight: Boolean,
): Modifier = if (lightweight) this else graphicsLayer {
    val distance = abs(offset()).coerceIn(0f, 1f)
    val depth = distance * distance * (3f - 2f * distance)
    val scale = 1f - (if (cover) 0.055f else 0.018f) * depth
    scaleX = scale
    scaleY = scale
    alpha = if (cover) 1f - 0.55f * depth else 1f
    transformOrigin = TransformOrigin(0.5f, if (cover) 0.42f else 0.15f)
}

/** Small stagger windows create a single reversible gesture, rather than queued animations. */
internal fun Modifier.lyricsPageEntrance(
    reveal: () -> Float,
    lightweight: Boolean,
    travelDp: Float,
    start: Float = 0f,
): Modifier = if (lightweight) this else graphicsLayer {
    val progress = ((reveal() - start) / (1f - start)).coerceIn(0f, 1f)
    val eased = progress * progress * (3f - 2f * progress)
    alpha = eased
    translationY = travelDp * density * (1f - eased)
}
