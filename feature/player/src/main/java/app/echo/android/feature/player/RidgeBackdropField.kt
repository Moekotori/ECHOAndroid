package app.echo.android.feature.player

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import app.echo.android.design.ArtworkColorGrid
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Stacked ridge lines whose height follows the cover's brightness row by row, so the cover's shapes rise as
 * ridges and the line colors come from the cover. Each line only fills a narrow band below itself to hide the
 * line behind it, which keeps overdraw low. It ignores the lyric pulse: the stack only drifts.
 * Heights, colors and paints are built once; a frame only does arithmetic and drawPath.
 */
internal class RidgeBackdropField(deep: Color, grid: ArtworkColorGrid, dark: Boolean) : BackdropField {
    private val fill = lerp(deep, if (dark) Color.Black else Color.White, if (dark) 0.3f else 0.15f).toArgb()
    private val basePaint = Paint().apply { color = fill }
    private val areaPaint = Paint().apply { color = fill }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val heights = buildHeights(grid)
    private val lineColors = IntArray(Lines) { line ->
        val cover = Color(grid.sample(0.5f, line / (Lines - 1f)))
        lerp(cover, if (dark) Color.White else Color(0xFF0A0A1E), if (dark) 0.4f else 0.25f).toArgb()
    }
    private val window = FloatArray(Points) { sin(PI * it / (Points - 1)).pow(0.6).toFloat() }
    private val line = Path()
    private val area = Path()
    private val ys = FloatArray(Points)

    override fun draw(canvas: Canvas, width: Float, height: Float, elapsedMs: Long, opacity: Float, pulse: Float) {
        if (width <= 0f || height <= 0f || opacity <= 0f) return
        val alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        basePaint.alpha = alpha
        canvas.drawRect(0f, 0f, width, height, basePaint)
        areaPaint.alpha = alpha
        strokePaint.strokeWidth = width * 0.005f
        val time = (elapsedMs + BackdropStillOffsetMs) / 1000f
        val amp = height * 0.085f
        val depth = height * 0.84f / (Lines - 1) + 1.7f * amp
        for (i in 0 until Lines) {
            val v = i / (Lines - 1f)
            val baseY = height * (0.10f + 0.84f * v)
            val phase = i * 0.55f
            val phase2 = i * 0.21f
            line.rewind()
            for (j in 0 until Points) {
                val u = j / (Points - 1f)
                val noise = sin(u * 9f + phase + time * 0.6f) * sin(u * 4.3f - phase2 - time * 0.35f)
                val h = heights[i * Points + j]
                val y = baseY - amp * window[j] * (h + 0.28f * noise * (0.4f + h))
                ys[j] = y
                if (j == 0) line.moveTo(0f, y) else line.lineTo(width * u, y)
            }
            area.set(line)
            for (j in Points - 1 downTo 0) area.lineTo(width * j / (Points - 1f), ys[j] + depth)
            area.close()
            canvas.drawPath(area, areaPaint)
            strokePaint.color = lineColors[i]
            strokePaint.alpha = (alpha * 0.9f).toInt()
            canvas.drawPath(line, strokePaint)
        }
    }

    private companion object {
        const val Lines = 46
        const val Points = 64

        fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

        fun luminance(color: Int): Float =
            (0.2126f * (color shr 16 and 0xFF) + 0.7152f * (color shr 8 and 0xFF) + 0.0722f * (color and 0xFF)) / 255f

        /** Smoothed cover brightness resampled to Lines x Points, normalized to 0..1 and eased by a power curve. */
        fun buildHeights(grid: ArtworkColorGrid): FloatArray {
            val cols = grid.columns
            val rows = grid.rows
            val lum = FloatArray(cols * rows) { luminance(grid.colorAt(it % cols, it / cols)) }
            val smooth = FloatArray(lum.size)
            for (y in 0 until rows) for (x in 0 until cols) {
                var sum = 0f
                var count = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx
                    val yy = y + dy
                    if (xx !in 0 until cols || yy !in 0 until rows) continue
                    sum += lum[yy * cols + xx]
                    count++
                }
                smooth[y * cols + x] = sum / count
            }
            val lo = smooth.min()
            val span = max(0.001f, smooth.max() - lo)
            val out = FloatArray(Lines * Points)
            for (i in 0 until Lines) {
                val fy = i / (Lines - 1f) * (rows - 1)
                val y0 = fy.toInt().coerceAtMost(rows - 2)
                val ty = fy - y0
                for (j in 0 until Points) {
                    val fx = j / (Points - 1f) * (cols - 1)
                    val x0 = fx.toInt().coerceAtMost(cols - 2)
                    val tx = fx - x0
                    val top = mix(smooth[y0 * cols + x0], smooth[y0 * cols + x0 + 1], tx)
                    val bottom = mix(smooth[(y0 + 1) * cols + x0], smooth[(y0 + 1) * cols + x0 + 1], tx)
                    out[i * Points + j] = ((mix(top, bottom, ty) - lo) / span).pow(1.5f)
                }
            }
            return out
        }
    }
}
