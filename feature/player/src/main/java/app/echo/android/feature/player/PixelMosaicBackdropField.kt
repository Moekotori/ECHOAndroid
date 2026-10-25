package app.echo.android.feature.player

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.echo.android.design.ArtworkColorGrid
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sin

/**
 * The cover as a wall of large square pixels. Each pixel flickers a little on its own phase and a pale band
 * sweeps down the page. Colors are looked up once per size; a frame only scales channels and calls drawRect.
 */
internal class PixelMosaicBackdropField(deep: Color, private val grid: ArtworkColorGrid) : BackdropField {
    private val base = Paint().apply { color = deep.toArgb() }
    private val cellPaint = Paint()
    private var laidOutWidth = 0f
    private var laidOutHeight = 0f
    private var rows = 0
    private var cell = 0f
    private var red = IntArray(0)
    private var green = IntArray(0)
    private var blue = IntArray(0)
    private var phase = FloatArray(0)

    override fun draw(canvas: Canvas, width: Float, height: Float, elapsedMs: Long, opacity: Float, pulse: Float) {
        if (width <= 0f || height <= 0f || opacity <= 0f) return
        if (width != laidOutWidth || height != laidOutHeight) layout(width, height)
        val alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        base.alpha = alpha
        canvas.drawRect(0f, 0f, width, height, base)
        val time = (elapsedMs + BackdropStillOffsetMs) / 1000.0
        val sweep = ((time * SweepSpeed) % (height + 2 * SweepReach) - SweepReach).toFloat()
        val inset = cell * 0.05f
        for (row in 0 until rows) {
            val top = row * cell
            val near = (1f - abs(top + cell / 2f - sweep) / SweepReach).coerceAtLeast(0f) * 0.35f
            for (column in 0 until Columns) {
                val index = row * Columns + column
                val flicker = 1f + 0.08f * sin(time * 1.4 + phase[index]).toFloat()
                cellPaint.color = pack(
                    mixToWhite(red[index] * flicker, near),
                    mixToWhite(green[index] * flicker, near),
                    mixToWhite(blue[index] * flicker, near),
                    alpha,
                )
                val left = column * cell
                canvas.drawRect(left + inset, top + inset, left + cell - inset, top + cell - inset, cellPaint)
            }
        }
    }

    private fun layout(width: Float, height: Float) {
        laidOutWidth = width
        laidOutHeight = height
        cell = width / Columns
        rows = (ceil(height / cell).toInt()).coerceAtMost(MaxRows)
        val count = rows * Columns
        red = IntArray(count)
        green = IntArray(count)
        blue = IntArray(count)
        phase = FloatArray(count)
        for (row in 0 until rows) {
            val v = ((row + 0.5f) * cell / height).coerceIn(0f, 0.999f)
            for (column in 0 until Columns) {
                val color = grid.sample((column + 0.5f) / Columns, v)
                val index = row * Columns + column
                red[index] = color shr 16 and 0xFF
                green[index] = color shr 8 and 0xFF
                blue[index] = color and 0xFF
                phase[index] = ((column * 7 + row * 13) % 17).toFloat()
            }
        }
    }

    private fun mixToWhite(channel: Float, amount: Float): Int =
        (channel + (255f - channel) * amount).toInt().coerceIn(0, 255)

    private fun pack(r: Int, g: Int, b: Int, a: Int): Int = a shl 24 or (r shl 16) or (g shl 8) or b

    private companion object {
        const val Columns = 18
        const val MaxRows = 60
        const val SweepSpeed = 60.0
        const val SweepReach = 80f
    }
}
