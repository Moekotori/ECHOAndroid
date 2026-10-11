package app.echo.android.feature.player

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import app.echo.android.design.ArtworkPalette
import kotlin.math.cos
import kotlin.math.sin

/** Soft, overlapping elliptical fields. Shaders, paints and matrices are reused on every frame. */
internal class FluidArtworkField(palette: ArtworkPalette) {
    private val base = Paint().apply { color = palette.deep.toArgb() }
    private val fields = arrayOf(
        Field(palette.vibrant, 0.22f, 0.20f, 0.0),
        Field(palette.soft, 0.82f, 0.35f, 2.1),
        Field(lerp(palette.vibrant, palette.soft, 0.35f), 0.28f, 0.85f, 4.2),
        Field(palette.deep, 0.80f, 0.90f, 5.4),
    )

    fun draw(canvas: Canvas, width: Float, height: Float, elapsedMs: Long, opacity: Float) {
        if (width <= 0f || height <= 0f || opacity <= 0f) return
        val alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        base.alpha = alpha
        canvas.drawRect(0f, 0f, width, height, base)
        val time = elapsedMs / 1000.0
        for (field in fields) {
            val phase = field.phase
            val x = width * (field.x + 0.24f * sin(time * 0.13 + phase).toFloat())
            val y = height * (field.y + 0.20f * cos(time * 0.11 + phase).toFloat())
            val breathing = 1f + 0.12f * sin(time * 0.09 + phase).toFloat()
            field.matrix.setScale(width * 0.80f * breathing, height * 0.62f / breathing)
            field.matrix.postRotate((sin(time * 0.07 + phase) * 28.0).toFloat())
            field.matrix.postTranslate(x, y)
            field.shader.setLocalMatrix(field.matrix)
            field.paint.alpha = alpha
            canvas.drawRect(0f, 0f, width, height, field.paint)
        }
    }

    private class Field(color: Color, val x: Float, val y: Float, val phase: Double) {
        val matrix = Matrix()
        val shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(color.toArgb(), color.copy(alpha = 0.68f).toArgb(), color.copy(alpha = 0f).toArgb()),
            floatArrayOf(0f, 0.38f, 1f), Shader.TileMode.CLAMP,
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = this@Field.shader }
    }
}
