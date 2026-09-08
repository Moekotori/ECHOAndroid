package app.echo.android.design

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Cached, bounded bitmap blur for Android versions without RenderEffect. */
class EchoLegacyBackgroundBlur(
    private val radiusDp: Float,
    private val viewportWidthDp: Int,
    private val viewportHeightDp: Int,
) : Transformation {
    override val cacheKey = "echo-background-blur-v1:$radiusDp:$viewportWidthDp:$viewportHeightDp"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = withContext(Dispatchers.Default) {
        val width = input.width
        val height = input.height
        // Convert the displayed dp radius to the sampled image's pixel coordinates (Crop).
        val radius = (radiusDp * minOf(
            width.toFloat() / viewportWidthDp,
            height.toFloat() / viewportHeightDp,
        ) / 2f).roundToInt().coerceIn(1, 64)
        val pixels = IntArray(width * height)
        val scratch = IntArray(pixels.size)
        input.getPixels(pixels, 0, width, 0, 0, width, height)
        // Three separable box passes approximate a Gaussian without a full-resolution GPU layer.
        repeat(3) {
            blurBackgroundPixels(pixels, scratch, width, height, radius, horizontal = true) { ensureActive() }
            blurBackgroundPixels(scratch, pixels, width, height, radius, horizontal = false) { ensureActive() }
        }
        ensureActive()
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}

internal fun blurBackgroundPixels(
    source: IntArray,
    target: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean,
    checkCancelled: () -> Unit = {},
) {
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    val stride = if (horizontal) 1 else width
    val count = radius * 2 + 1
    for (line in 0 until lines) {
        checkCancelled()
        val base = if (horizontal) line * width else line
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        fun accumulate(position: Int, sign: Int) {
            val pixel = source[base + position.coerceIn(0, length - 1) * stride]
            val a = pixel ushr 24
            alpha += a * sign
            // Premultiply to avoid transparent pixels introducing dark/color fringes.
            red += ((pixel ushr 16 and 255) * a) * sign
            green += ((pixel ushr 8 and 255) * a) * sign
            blue += ((pixel and 255) * a) * sign
        }
        for (offset in -radius..radius) accumulate(offset, 1)
        for (position in 0 until length) {
            val divisor = max(alpha, 1)
            target[base + position * stride] = ((alpha / count) shl 24) or
                ((red / divisor) shl 16) or ((green / divisor) shl 8) or (blue / divisor)
            accumulate(position - radius, -1)
            accumulate(position + radius + 1, 1)
        }
    }
}
