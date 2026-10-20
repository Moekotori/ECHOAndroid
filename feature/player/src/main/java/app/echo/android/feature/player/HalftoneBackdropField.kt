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
 */
internal class HalftoneBackdropField(deep: Color, private val grid: ArtworkColorGrid) : BackdropField {
    private val base = Paint().apply { color = deep.toArgb() }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private var laidOutWidth = 0f
    private var laidOutHeight = 0f
    private var rows = 0
    private var cell = 0f
    private var colors = IntArray(0)
    private var luminance = FloatArray(0)
    private var rowFade = FloatArray(0)

    override fun draw(canvas: Canvas, width: Float, height: Float, elapsedMs: Long, opacity: Float) {
        if (width <= 0f || height <= 0f || opacity <= 0f) return
        if (width != laidOutWidth || height != laidOutHeight) layout(width, height)
        val alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        base.alpha = alpha
        canvas.drawRect(0f, 0f, width, height, base)
        val time = (elapsedMs + BackdropStillOffsetMs) / 1000.0
        val waveX = Columns * (0.5f + 0.4f * sin(time * 0.18).toFloat())
        val waveY = rows * (0.3f + 0.25f * cos(time * 0.13).toFloat())
        val phase = time * 1.3
        for (row in 0 until rows) {
            val dotAlpha = (alpha * rowFade[row]).toInt()
            val cy = (row + 0.5f) * cell
            for (column in 0 until Columns) {
                val index = row * Columns + column
                val distance = hypot(column - waveX, row - waveY)
                val wave = 0.5f + 0.5f * sin(distance * 0.55 - phase).toFloat()
                val radius = cell * 0.5f * (0.14f + 0.8f * wave * (0.35f + 0.65f * luminance[index]))
                dot.color = colors[index]
                dot.alpha = dotAlpha
                canvas.drawCircle((column + 0.5f) * cell, cy, radius, dot)
            }
        }
    }

    private fun layout(width: Float, height: Float) {
        laidOutWidth = width
        laidOutHeight = height
        cell = width / Columns
        rows = (ceil(height / cell).toInt() + 1).coerceAtMost(MaxRows)
        colors = IntArray(rows * Columns)
        luminance = FloatArray(rows * Columns)
        rowFade = FloatArray(rows)
        for (row in 0 until rows) {
            val v = ((row + 0.5f) * cell / height).coerceIn(0f, 0.999f)
            val offset = (v - 0.5f) / 0.2f
            rowFade[row] = 1f - 0.5f * exp(-(offset * offset))
            for (column in 0 until Columns) {
                val u = (column + 0.5f) / Columns
                val color = grid.colorAt((u * grid.columns).toInt(), (v * grid.rows).toInt())
                val index = row * Columns + column
                colors[index] = color
                luminance[index] = (0.2126f * (color shr 16 and 0xFF) + 0.7152f * (color shr 8 and 0xFF) + 0.0722f * (color and 0xFF)) / 255f
            }
        }
    }

    private companion object {
        const val Columns = 14
        const val MaxRows = 64
    }
}
