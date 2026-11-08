package app.echo.android.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/** Position the image inside its existing viewport without enlarging its blur layer. */
fun backgroundImageAlignment(positionX: Float, positionY: Float): Alignment = object : Alignment {
    override fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection) = IntOffset(
        backgroundPanOffset(space.width, size.width, positionX),
        backgroundPanOffset(space.height, size.height, positionY),
    )
}

fun Modifier.backgroundImageTransform(scale: Float, positionX: Float, positionY: Float): Modifier = graphicsLayer {
    scaleX = scale.coerceIn(1f, 1.4f)
    scaleY = scaleX
    transformOrigin = TransformOrigin((1f - positionX.coerceIn(-1f, 1f)) / 2f, (1f - positionY.coerceIn(-1f, 1f)) / 2f)
}

/** Crop and pan within the available overflow, so positioning never exposes an empty edge. */
@Composable
fun EchoBackgroundViewport(
    aspectRatio: Float,
    scale: Float,
    positionX: Float,
    positionY: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier.clipToBounds()) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val ratio = aspectRatio.takeIf { it.isFinite() && it > 0f }
            ?: (width.toFloat() / height.coerceAtLeast(1))
        val zoom = scale.coerceIn(1f, 1.4f)
        val childWidth = (maxOf(width.toFloat(), height * ratio) * zoom).roundToInt().coerceAtLeast(width)
        val childHeight = (maxOf(height.toFloat(), width / ratio) * zoom).roundToInt().coerceAtLeast(height)
        val child = measurables.single().measure(Constraints.fixed(childWidth, childHeight))
        layout(width, height) {
            // Absolute placement keeps left/right consistent in RTL languages too.
            child.place(backgroundPanOffset(width, childWidth, positionX), backgroundPanOffset(height, childHeight, positionY))
        }
    }
}

internal fun backgroundPanOffset(viewport: Int, content: Int, position: Float): Int {
    val safePosition = position.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
    return ((viewport - content).coerceAtMost(0) * (1f - safePosition) / 2f).roundToInt()
}
