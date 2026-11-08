package app.echo.android.feature.player

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import app.echo.android.design.ArtworkColorGrid
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * A slow-moving color flow in the spirit of Apple Music's lyrics background: a few very large, soft
 * discs take the cover's colors from where they sit in the artwork, then drift on wide elliptical
 * orbits while stretching and turning, so the colors keep folding into each other.
 * Shaders and paints are built once; motion only transforms recorded canvas commands.
 */
internal class FlowBackdropField(deep: Color, grid: ArtworkColorGrid) : BackdropField {
    private val base = Paint().apply { color = deep.toArgb() }
    private val blobs = Array(Specs.size) { Blob(Specs[it], grid) }

    override fun draw(canvas: Canvas, width: Float, height: Float, elapsedMs: Long, opacity: Float, pulse: Float) {
        if (width <= 0f || height <= 0f || opacity <= 0f) return
        val alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        base.alpha = alpha
        canvas.drawRect(0f, 0f, width, height, base)
        val time = (elapsedMs + BackdropStillOffsetMs) / 1000.0
        val reach = max(width, height)
        for (blob in blobs) blob.drawAt(canvas, width, height, reach, time, alpha)
    }

    private class Spec(
        val cx: Float,
        val cy: Float,
        val orbitX: Float,
        val orbitY: Float,
        /** Radians per second; the sign sets the direction so neighbours counter-rotate. */
        val speed: Double,
        val phase: Double,
        /** Disc radius as a share of the screen's longer edge. */
        val radius: Float,
        /** Toward white (> 0) or black (< 0), so the flow has light and shade even on flat covers. */
        val lift: Float,
    )

    private class Blob(private val spec: Spec, grid: ArtworkColorGrid) {
        private val paint: Paint

        init {
            val sampled = Color(grid.sample(spec.cx, spec.cy))
            val color = when {
                spec.lift > 0f -> lerp(sampled, Color.White, spec.lift)
                spec.lift < 0f -> lerp(sampled, Color.Black, -spec.lift)
                else -> sampled
            }
            val shader = RadialGradient(
                0f, 0f, 1f,
                intArrayOf(withAlpha(color, 0.96f), withAlpha(color, 0.82f), withAlpha(color, 0.34f), withAlpha(color, 0f)),
                floatArrayOf(0f, 0.42f, 0.74f, 1f), Shader.TileMode.CLAMP,
            )
            paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        }

        fun drawAt(canvas: Canvas, width: Float, height: Float, reach: Float, time: Double, alpha: Int) {
            val angle = spec.speed * time + spec.phase
            val x = width * (spec.cx + spec.orbitX * cos(angle).toFloat())
            val y = height * (spec.cy + spec.orbitY * sin(angle * 0.83 + spec.phase * 1.7).toFloat())
            // Stretch along one axis while the other relaxes, and turn slowly: the disc reads as a flowing shape.
            val stretch = 1f + 0.22f * sin(angle * 0.7 + spec.phase).toFloat()
            val radius = reach * spec.radius
            paint.alpha = alpha
            val checkpoint = canvas.save()
            canvas.translate(x, y)
            canvas.rotate((sin(angle * 0.45 + spec.phase) * 70.0).toFloat())
            canvas.scale(radius * stretch, radius / stretch)
            canvas.drawCircle(0f, 0f, 1f, paint)
            canvas.restoreToCount(checkpoint)
        }
    }

    private companion object {
        // Five large discs carry the cover's main colors; four smaller ones add light and shade between them.
        val Specs = arrayOf(
            Spec(0.25f, 0.20f, 0.22f, 0.14f, 0.31, 0.0, 0.70f, 0f),
            Spec(0.80f, 0.30f, 0.20f, 0.16f, -0.27, 1.6, 0.66f, 0.05f),
            Spec(0.50f, 0.52f, 0.28f, 0.18f, 0.22, 3.1, 0.72f, -0.04f),
            Spec(0.20f, 0.78f, 0.22f, 0.16f, -0.35, 4.4, 0.68f, 0.04f),
            Spec(0.80f, 0.85f, 0.20f, 0.12f, 0.29, 5.5, 0.64f, -0.08f),
            Spec(0.50f, 0.14f, 0.30f, 0.10f, -0.42, 0.9, 0.40f, 0.10f),
            Spec(0.15f, 0.45f, 0.15f, 0.25f, 0.38, 2.4, 0.42f, 0.08f),
            Spec(0.85f, 0.60f, 0.15f, 0.25f, -0.33, 3.8, 0.40f, 0.10f),
            Spec(0.50f, 0.90f, 0.30f, 0.08f, 0.40, 5.0, 0.40f, -0.12f),
        )

        fun withAlpha(color: Color, alpha: Float): Int = color.copy(alpha = alpha).toArgb()
    }
}
