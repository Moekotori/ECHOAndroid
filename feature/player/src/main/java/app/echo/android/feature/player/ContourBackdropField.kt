package app.echo.android.feature.player

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import app.echo.android.design.ArtworkColorGrid

/**
 * The cover's brightness read as terrain, drawn as contour lines from the darkest to the lightest ridge.
 * The brightness field is built once per size; each frame walks it once per level (marching squares) and
 * the whole set drifts slowly through the brightness range. Paths and paints are reused.
 */
internal class ContourBackdropField(
    deep: Color,
    low: Color,
    high: Color,
    private val grid: ArtworkColorGrid,
) : BackdropField {
    private val base = Paint().apply { color = deep.toArgb() }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private val levelColors = IntArray(Levels) { lerp(lerp(low, Color.White, 0.45f), lerp(high, Color.White, 0.35f), it / (Levels - 1f)).toArgb() }
    private val field = FloatArray(grid.columns * grid.rows)

    init {
        var darkest = Float.MAX_VALUE
        var lightest = -Float.MAX_VALUE
        for (row in 0 until grid.rows) for (column in 0 until grid.columns) {
            val color = grid.colorAt(column, row)
            val value = (0.2126f * (color shr 16 and 0xFF) + 0.7152f * (color shr 8 and 0xFF) + 0.0722f * (color and 0xFF)) / 255f
            field[row * grid.columns + column] = value
            if (value < darkest) darkest = value
            if (value > lightest) lightest = value
        }
        // The mosaic keeps lightness inside a narrow band; spread it so every level crosses the picture.
        val span = (lightest - darkest).coerceAtLeast(0.02f)
        for (index in field.indices) field[index] = (field[index] - darkest) / span
    }

    override fun draw(canvas: Canvas, width: Float, height: Float, elapsedMs: Long, opacity: Float, pulse: Float) {
        if (width <= 0f || height <= 0f || opacity <= 0f) return
        val alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        base.alpha = alpha
        canvas.drawRect(0f, 0f, width, height, base)
        line.strokeWidth = width / 240f
        val drift = ((elapsedMs + BackdropStillOffsetMs) / 1000.0 * 0.05 % 1.0).toFloat()
        val columns = grid.columns
        val rows = grid.rows
        val stepX = width / (columns - 1)
        val stepY = height / (rows - 1)
        for (level in 0 until Levels) {
            val threshold = (level + drift) / Levels
            path.reset()
            for (row in 0 until rows - 1) {
                val y = row * stepY
                for (column in 0 until columns - 1) {
                    val a = field[row * columns + column]
                    val b = field[row * columns + column + 1]
                    val c = field[(row + 1) * columns + column + 1]
                    val d = field[(row + 1) * columns + column]
                    val above = (if (a >= threshold) 1 else 0) or (if (b >= threshold) 2 else 0) or
                        (if (c >= threshold) 4 else 0) or (if (d >= threshold) 8 else 0)
                    if (above == 0 || above == 15) continue
                    val x = column * stepX
                    // Crossing points on the cell's top, right, bottom and left edges.
                    val topX = x + stepX * ((threshold - a) / (b - a))
                    val rightY = y + stepY * ((threshold - b) / (c - b))
                    val bottomX = x + stepX * ((threshold - d) / (c - d))
                    val leftY = y + stepY * ((threshold - a) / (d - a))
                    when (above) {
                        1, 14 -> segment(x, leftY, topX, y)
                        2, 13 -> segment(topX, y, x + stepX, rightY)
                        3, 12 -> segment(x, leftY, x + stepX, rightY)
                        4, 11 -> segment(x + stepX, rightY, bottomX, y + stepY)
                        5 -> { segment(x, leftY, topX, y); segment(x + stepX, rightY, bottomX, y + stepY) }
                        6, 9 -> segment(topX, y, bottomX, y + stepY)
                        7, 8 -> segment(x, leftY, bottomX, y + stepY)
                        10 -> { segment(topX, y, x + stepX, rightY); segment(x, leftY, bottomX, y + stepY) }
                    }
                }
            }
            line.color = levelColors[level]
            line.alpha = alpha
            canvas.drawPath(path, line)
        }
    }

    private fun segment(x1: Float, y1: Float, x2: Float, y2: Float) {
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
    }

    private companion object {
        const val Levels = 18
    }
}
