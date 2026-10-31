package app.echo.android.feature.player

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.echo.android.design.ArtworkColorGrid
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Soft light blobs placed on a 3x4 lattice; each takes the color of the cover at its own position,
 * so the colors on screen line up with the colors in the artwork. Shaders and paints are reused.
 */
internal class MeshBackdropField(deep: Color, grid: ArtworkColorGrid) : BackdropField {
    private val base = Paint().apply { color = deep.toArgb() }
    private val blobs = Array(Columns * Rows) { index ->
        val u = (index % Columns) / (Columns - 1f)
        val v = (index / Columns) / (Rows - 1f)
        Blob(grid.sample(u, v), u, v, index)
    }
    private val highlight = Blob(0xFFFFFFFF.toInt(), 0.5f, 0.28f, 0, centerAlpha = 0.16f, midAlpha = 0.06f)

    override fun draw(canvas: Canvas, width: Float, height: Float, elapsedMs: Long, opacity: Float, pulse: Float) {
        if (width <= 0f || height <= 0f || opacity <= 0f) return
        val alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        base.alpha = alpha
        canvas.drawRect(0f, 0f, width, height, base)
        val time = (elapsedMs + BackdropStillOffsetMs) / 1000.0
        val radius = max(width, height) * 0.46f
        for (blob in blobs) {
            val phase = blob.phase
            val x = width * (blob.u + 0.13f * sin(time * 0.17 + phase * 1.9).toFloat())
            val y = height * (blob.v + 0.09f * cos(time * 0.13 + phase * 2.3).toFloat())
            blob.drawAt(canvas, x, y, radius, alpha)
        }
        val hx = width * (0.5f + 0.22f * sin(time * 0.2).toFloat())
        val hy = height * (0.28f + 0.10f * cos(time * 0.17).toFloat())
        highlight.drawAt(canvas, hx, hy, width * 0.6f, alpha)
    }

    private class Blob(
        color: Int,
        val u: Float,
        val v: Float,
        index: Int,
        centerAlpha: Float = 0.95f,
        midAlpha: Float = 0.5f,
    ) {
        val phase = index.toDouble()
        private val shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(withAlpha(color, centerAlpha), withAlpha(color, midAlpha), withAlpha(color, 0f)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = this@Blob.shader }

        /** Transform drawing commands instead of mutating the cached shader's local matrix. */
        fun drawAt(canvas: Canvas, x: Float, y: Float, radius: Float, alpha: Int) {
            paint.alpha = alpha
            val checkpoint = canvas.save()
            canvas.translate(x, y)
            canvas.scale(radius, radius)
            canvas.drawCircle(0f, 0f, 1f, paint)
            canvas.restoreToCount(checkpoint)
        }
    }

    private companion object {
        const val Columns = 3
        const val Rows = 4

        fun withAlpha(color: Int, alpha: Float): Int = (color and 0x00FFFFFF) or ((alpha * 255f).toInt() shl 24)
    }
}
