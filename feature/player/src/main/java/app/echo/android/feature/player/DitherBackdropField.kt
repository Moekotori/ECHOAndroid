package app.echo.android.feature.player

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import app.echo.android.design.ArtworkColorGrid
import kotlin.math.ceil
import kotlin.math.sin

/**
 * The cover as a two-color ordered dither: pixels of the palette's lightest color where the cover is bright
 * (after a 4×4 Bayer threshold), the darkest elsewhere. The threshold drifts slowly so the picture shimmers.
 * Brightness is looked up once per size; a frame compares thresholds and draws every lit pixel in one call.
 */
internal class DitherBackdropField(deep: Color, soft: Color, private val grid: ArtworkColorGrid) : BackdropField {
    private val dark = Paint().apply { color = lerp(deep, Color.Black, 0.4f).toArgb() }
    private val lit = Paint().apply {
        color = lerp(soft, Color.White, 0.2f).toArgb()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
    }
    private var laidOutWidth = 0f
    private var laidOutHeight = 0f
    private var rows = 0
    private var brightness = FloatArray(0)
    private var points = FloatArray(0)

    override fun draw(canvas: Canvas, width: Float, height: Float, elapsedMs: Long, opacity: Float, pulse: Float) {
        if (width <= 0f || height <= 0f || opacity <= 0f) return
        if (width != laidOutWidth || height != laidOutHeight) layout(width, height)
        val alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        dark.alpha = alpha
        lit.alpha = alpha
        canvas.drawRect(0f, 0f, width, height, dark)
        val cell = width / Columns
        lit.strokeWidth = cell
        val shift = 0.08f * sin((elapsedMs + BackdropStillOffsetMs) / 1000.0 * 0.5).toFloat()
        var count = 0
        for (row in 0 until rows) for (column in 0 until Columns) {
            val threshold = (Bayer[(row and 3) * 4 + (column and 3)] + 0.5f) / 16f
            if ((brightness[row * Columns + column] + shift) * 1.15f > threshold) {
                points[count++] = (column + 0.5f) * cell
                points[count++] = (row + 0.5f) * cell
            }
        }
        canvas.drawPoints(points, 0, count, lit)
    }

    private fun layout(width: Float, height: Float) {
        laidOutWidth = width
        laidOutHeight = height
        val cell = width / Columns
        rows = ceil(height / cell).toInt().coerceAtMost(MaxRows)
        brightness = FloatArray(rows * Columns)
        points = FloatArray(rows * Columns * 2)
        var low = Float.MAX_VALUE
        var high = -Float.MAX_VALUE
        for (row in 0 until rows) {
            val v = ((row + 0.5f) * cell / height).coerceIn(0f, 0.999f)
            for (column in 0 until Columns) {
                val color = grid.sample((column + 0.5f) / Columns, v)
                val value = (0.2126f * (color shr 16 and 0xFF) + 0.7152f * (color shr 8 and 0xFF) + 0.0722f * (color and 0xFF)) / 255f
                brightness[row * Columns + column] = value
                if (value < low) low = value
                if (value > high) high = value
            }
        }
        // The mosaic keeps lightness inside a narrow band; spread it so both colors appear.
        val span = (high - low).coerceAtLeast(0.02f)
        for (index in brightness.indices) brightness[index] = (brightness[index] - low) / span
    }

    private companion object {
        const val Columns = 60
        const val MaxRows = 160
        val Bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
    }
}
