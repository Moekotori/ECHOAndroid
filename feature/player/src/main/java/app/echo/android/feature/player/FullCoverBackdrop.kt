package app.echo.android.feature.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkColorGrid
import app.echo.android.design.ArtworkPalette
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode

/** Frame the artwork on tall screens and extend its edge colors behind the player chrome. */
@Composable
internal fun FullCoverBackdrop(
    artworkUri: String?,
    colorGrid: ArtworkColorGrid?,
    fallback: ArtworkPalette,
    spreadControls: Boolean,
    reveal: () -> Float,
    modifier: Modifier = Modifier,
) {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val header = remember(colorGrid, fallback) {
        (colorGrid?.edgeColor(top = true) ?: fallback.vibrant).withLuminanceAtMost(0.175f)
    }
    val lowerEdge = remember(colorGrid, fallback) { colorGrid?.edgeColor(top = false) ?: fallback.deep }
    val controls = remember(lowerEdge) { lowerEdge.withLuminanceAtMost(0.12f) }
    val footer = remember(controls) { controls.withLuminanceAtMost(controls.luminance() * 0.72f) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val framed = !spreadControls && maxWidth <= maxHeight * 0.72f
        val artworkTop = if (framed) maxHeight * 0.125f else 0.dp
        val artworkHeight = if (framed) minOf(maxWidth, maxHeight * 0.49f) else maxHeight

        Crossfade(
            targetState = artworkUri,
            modifier = Modifier.fillMaxSize().drawWithCache {
                val surface = Brush.verticalGradient(0f to header, 0.62f to controls, 1f to footer)
                val veil = if (framed) {
                    val start = artworkTop.roundToPx().toFloat()
                    val height = artworkHeight.roundToPx().toFloat()
                    val viewportHeight = size.height.coerceAtLeast(1f)
                    Brush.verticalGradient(
                        0f to header,
                        start / viewportHeight to header,
                        (start + height * 0.19f) / viewportHeight to header.copy(alpha = 0f),
                        (start + height * 0.77f) / viewportHeight to controls.copy(alpha = 0f),
                        (start + height) / viewportHeight to controls,
                        1f to footer,
                    )
                } else {
                    // Wide layouts can put controls beside the cover, anywhere along the vertical axis.
                    Brush.verticalGradient(0f to header, 0.42f to controls.copy(alpha = 0.94f), 1f to footer)
                }
                onDrawWithContent {
                    drawRect(surface)
                    drawContent()
                    drawRect(veil)
                    val lyrics = reveal().coerceIn(0f, 1f)
                    if (lyrics > 0f) drawRect(Color.Black.copy(alpha = LyricsDim * lyrics))
                }
            },
            animationSpec = if (lightweight) snap() else tween(420, easing = EchoMotion.Silk),
            label = "full-cover-artwork",
        ) { uri ->
            if (!uri.isNullOrBlank()) {
                EchoArtworkImage(
                    artworkUri = uri,
                    contentDescription = null,
                    modifier = Modifier.offset(y = artworkTop).fillMaxWidth().height(artworkHeight),
                    shape = RectangleShape,
                    sizeClass = EchoArtworkSize.Hero,
                )
            }
        }
    }
}

/** Favor colored pixels near the edge so dark line work and bright lettering do not muddy the wash. */
private fun ArtworkColorGrid.edgeColor(top: Boolean): Color {
    val band = (rows / 5).coerceAtLeast(1)
    val start = if (top) 0 else rows - band
    var red = 0f
    var green = 0f
    var blue = 0f
    var total = 0f
    for (row in start until start + band) {
        for (column in 0 until columns) {
            val pixel = Color(colorAt(column, row))
            val chroma = maxOf(pixel.red, pixel.green, pixel.blue) - minOf(pixel.red, pixel.green, pixel.blue)
            val weight = 0.06f + chroma * chroma
            red += pixel.red * weight
            green += pixel.green * weight
            blue += pixel.blue * weight
            total += weight
        }
    }
    return Color(red / total, green / total, blue / total)
}

/** Scale in linear RGB to keep the cover's hue while reserving contrast for white content. */
private fun Color.withLuminanceAtMost(limit: Float): Color {
    val light = luminance()
    if (light <= limit) return this
    val linear = convert(ColorSpaces.LinearSrgb)
    val scale = limit / light
    return Color(linear.red * scale, linear.green * scale, linear.blue * scale, colorSpace = ColorSpaces.LinearSrgb)
        .convert(ColorSpaces.Srgb)
}

private const val LyricsDim = 0.66f
