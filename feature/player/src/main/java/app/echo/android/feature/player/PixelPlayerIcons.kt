package app.echo.android.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Pixelarticons assets, with upstream MIT attribution bundled with the app. */
internal object PixelPlayerIcons {
    val Play: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.pixel_play)
    val Pause: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.pixel_pause)
    val Previous: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.pixel_prev)
    val Next: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.pixel_next)
    val Shuffle: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.pixel_shuffle)
    val Repeat: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.pixel_repeat)
    val Queue: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.pixel_playlist)
    val Cast: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.pixel_cast)
    val Star: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.pixel_star)
    val Collapse: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.pixel_chevron_down)
    val More: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.pixel_more_horizontal)
}

/** Two square steps, rather than a diagonal chamfer or a rounded corner. */
internal object PixelFrameShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val n = minOf(with(density) { 5.dp.toPx() }, size.minDimension / 4f)
        val h = n / 2f
        val w = size.width
        val b = size.height
        return Outline.Generic(Path().apply {
            moveTo(n, 0f); lineTo(w - n, 0f); lineTo(w - n, h); lineTo(w - h, h)
            lineTo(w - h, n); lineTo(w, n); lineTo(w, b - n); lineTo(w - h, b - n)
            lineTo(w - h, b - h); lineTo(w - n, b - h); lineTo(w - n, b); lineTo(n, b)
            lineTo(n, b - h); lineTo(h, b - h); lineTo(h, b - n); lineTo(0f, b - n)
            lineTo(0f, n); lineTo(h, n); lineTo(h, h); lineTo(n, h); close()
        })
    }
}
