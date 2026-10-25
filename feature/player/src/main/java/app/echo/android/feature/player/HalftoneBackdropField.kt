package app.echo.android.feature.player

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.echo.android.design.ArtworkColorGrid
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The cover as a field of dots: every dot takes the color of the cover at its own position and swells
 * when a slow wave passes. Dots fade around the middle of the screen so lyrics stay readable.
 * Per-cell colors are looked up once per size; a frame only does arithmetic and drawCircle.
 * [dense] is the full-bleed variant: twice the dots, colors interpolated between mosaic cells, and a gentle swell.
 */
internal class HalftoneBackdropField(deep: Color, private val grid: ArtworkColorGrid, private val dense: Boolean = false) : BackdropField {
    private val columns = if (dense) DenseColumns else Columns
    private val base = Paint().apply { color = deep.toArgb() }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private var laidOutWidth = 0f
    private var laidOutHeight = 0f
    private var rows = 0
    private var cell = 0f
    private var colors = IntArray(0)
    private var luminance = FloatArray(0)
    private var rowFade = FloatArray(0)

    override fun draw(canvas: Canvas, width: Float, height: Float, elapsedMs: Long, opacity: Float, pulse: Float) {
        if (width <= 0f || height <= 0f || opacity <= 0f) return
        if (width != laidOutWidth || height != laidOutHeight) layout(width, height)
        val alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        base.alpha = alpha
        canvas.drawRect(0f, 0f, width, height, base)
        val time = (elapsedMs + BackdropStillOffsetMs) / 1000.0
        val waveX = columns * (0.5f + 0.4f * sin(time * 0.18).toFloat())
        val waveY = rows * (0.3f + 0.25f * cos(time * 0.13).toFloat())
        val phase = time * 1.3
        for (row in 0 until rows) {
            val dotAlpha = (alpha * rowFade[row]).toInt()
            val cy = (row + 0.5f) * cell
            for (column in 0 until columns) {
                val index = row * columns + column
                val radius = if (dense) {
                    val swell = 1f + 0.1f * sin(time * 0.8 + (column + row) * 0.35).toFloat()
                    cell * 0.5f * (0.12f + 0.88f * luminance[index]) * swell
                } else {
                    val distance = hypot(column - waveX, row - waveY)
                    val wave = 0.5f + 0.5f * sin(distance * 0.55 - phase).toFloat()
                    cell * 0.5f * (0.14f + 0.8f * wave * (0.35f + 0.65f * luminance[index]))
                }
                dot.color = colors[index]
                dot.alpha = dotAlpha
                canvas.drawCircle((column + 0.5f) * cell, cy, radius, dot)
            }
        }
    }

    private fun layout(width: Float, height: Float) {
        laidOutWidth = width
        laidOutHeight = height
        cell = width / columns
        rows = (ceil(height / cell).toInt() + 1).coerceAtMost(MaxRows)
        colors = IntArray(rows * columns)
        luminance = FloatArray(rows * columns)
        rowFade = FloatArray(rows)
        for (row in 0 until rows) {
            val v = ((row + 0.5f) * cell / height).coerceIn(0f, 0.999f)
            val offset = (v - 0.5f) / 0.2f
            rowFade[row] = 1f - (if (dense) 0.2f else 0.5f) * exp(-(offset * offset))
            for (column in 0 until columns) {
                val u = (column + 0.5f) / columns
                val color = if (dense) smooth(u, v) else grid.colorAt((u * grid.columns).toInt(), (v * grid.rows).toInt())
                val index = row * columns + column
                colors[index] = color
                luminance[index] = (0.2126f * (color shr 16 and 0xFF) + 0.7152f * (color shr 8 and 0xFF) + 0.0722f * (color and 0xFF)) / 255f
            }
        }
    }

    /** Bilinear blend of the four nearest mosaic cells, so twice as many dots do not show the cell edges. */
    private fun smooth(u: Float, v: Float): Int {
        val fx = (u * grid.columns - 0.5f).coerceIn(0f, grid.columns - 1f)
        val fy = (v * grid.rows - 0.5f).coerceIn(0f, grid.rows - 1f)
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val tx = fx - x0
        val ty = fy - y0
        var result = 0xFF shl 24
        for (shift in intArrayOf(16, 8, 0)) {
            fun channel(x: Int, y: Int) = (grid.colorAt(x, y) shr shift and 0xFF).toFloat()
            val top = channel(x0, y0) * (1 - tx) + channel(x0 + 1, y0) * tx
            val bottom = channel(x0, y0 + 1) * (1 - tx) + channel(x0 + 1, y0 + 1) * tx
            result = result or ((top * (1 - ty) + bottom * ty).toInt().coerceIn(0, 255) shl shift)
        }
        return result
    }

    private companion object {
        const val Columns = 14
        const val DenseColumns = 26
        const val MaxRows = 80
    }
}
