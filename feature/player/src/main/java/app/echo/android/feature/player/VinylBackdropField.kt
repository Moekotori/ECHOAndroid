package app.echo.android.feature.player

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import app.echo.android.design.ArtworkColorGrid
import kotlin.math.hypot
import kotlin.math.max

/**
 * The whole screen is a record's grooves; light only catches them where a cover-colored sheen sweeps past,
 * and the sheen turns slowly. A lyric [pulse] kicks the sheen back a little and brightens it. Grooves are
 * four stroked paths (different alphas) built once per size; the sheen is one SweepGradient whose matrix
 * rotates, so a frame is a handful of drawPath calls with no allocation.
 */
internal class VinylBackdropField(deep: Color, grid: ArtworkColorGrid, private val dark: Boolean) : BackdropField {
    private val base = Paint().apply {
        color = lerp(deep, if (dark) Color.Black else Color.White, if (dark) 0.4f else 0.3f).toArgb()
    }
    private val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = if (dark) 0xFFFFFFFF.toInt() else 0xFF1E1E32.toInt()
    }
    private val additive = if (dark) PorterDuffXfermode(PorterDuff.Mode.ADD) else null
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        xfermode = additive
    }
    private val bloomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = additive }
    private val glowShader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(withAlpha(grid.sample(0.5f, 0.35f), 0.5f), withAlpha(grid.sample(0.5f, 0.35f), 0f)),
        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
    )
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = glowShader }
    private val matrix = Matrix()
    private val sheenColors: IntArray
    private val groups = Array(Groups) { Path() }
    private var laidOutWidth = 0f
    private var laidOutHeight = 0f
    private var centerX = 0f
    private var centerY = 0f
    private var sweep: SweepGradient? = null

    init {
        val lift = if (dark) 0.3f else 0f
        val first = lerp(Color(grid.sample(0.2f, 0.25f)), Color.White, lift).toArgb()
        val second = lerp(Color(grid.sample(0.8f, 0.7f)), Color.White, lift).toArgb()
        val hot = lerp(Color(first), Color.White, 0.4f).toArgb()
        sheenColors = intArrayOf(
            withAlpha(first, 0f), withAlpha(first, 0.35f), withAlpha(hot, 1f), withAlpha(first, 0.35f), withAlpha(first, 0f),
            withAlpha(second, 0f), withAlpha(second, 0.85f), withAlpha(second, 0f), withAlpha(first, 0f),
        )
    }

    override fun draw(canvas: Canvas, width: Float, height: Float, elapsedMs: Long, opacity: Float, pulse: Float) {
        if (width <= 0f || height <= 0f || opacity <= 0f) return
        if (width != laidOutWidth || height != laidOutHeight) layout(width, height)
        val sweep = sweep ?: return
        val alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        val kick = pulse.coerceIn(0f, 1f)
        base.alpha = alpha
        canvas.drawRect(0f, 0f, width, height, base)
        val glowRadius = width * 0.95f
        matrix.setScale(glowRadius, glowRadius)
        matrix.postTranslate(centerX, centerY)
        glowShader.setLocalMatrix(matrix)
        glowPaint.alpha = alpha
        canvas.drawRect(centerX - glowRadius, centerY - glowRadius, centerX + glowRadius, centerY + glowRadius, glowPaint)
        matrix.setRotate((elapsedMs + BackdropStillOffsetMs) / 1000f * DegreesPerSecond - kick * 18f, centerX, centerY)
        sweep.setLocalMatrix(matrix)
        bloomPaint.alpha = (alpha * (0.10f + 0.10f * kick)).toInt()
        canvas.drawRect(0f, 0f, width, height, bloomPaint)
        for (group in 0 until Groups) {
            groovePaint.alpha = (alpha * GrooveAlpha[group]).toInt()
            canvas.drawPath(groups[group], groovePaint)
            sheenPaint.alpha = (alpha * (0.72f + 0.28f * kick) * SheenAlpha[group]).toInt().coerceAtMost(255)
            canvas.drawPath(groups[group], sheenPaint)
        }
    }

    private fun layout(width: Float, height: Float) {
        laidOutWidth = width
        laidOutHeight = height
        centerX = width * 0.5f
        centerY = height * 0.36f
        val shader = SweepGradient(centerX, centerY, sheenColors, SheenStops)
        sweep = shader
        sheenPaint.shader = shader
        bloomPaint.shader = shader
        val lineWidth = max(1f, width * 0.0042f)
        groovePaint.strokeWidth = lineWidth
        sheenPaint.strokeWidth = lineWidth
        groups.forEach { it.rewind() }
        val farthest = hypot(max(centerX, width - centerX), max(centerY, height - centerY))
        val step = width / 115f
        var radius = width * 0.12f
        var ring = 0
        while (radius < farthest && ring < MaxRings) {
            groups[ring % Groups].addCircle(centerX, centerY, radius, Path.Direction.CW)
            radius += step
            ring++
        }
    }

    private companion object {
        const val Groups = 4
        const val MaxRings = 260
        const val DegreesPerSecond = 8f
        val GrooveAlpha = floatArrayOf(0.14f, 0.10f, 0.07f, 0.12f)
        val SheenAlpha = floatArrayOf(0.7f, 1f, 0.85f, 0.95f)
        val SheenStops = floatArrayOf(0f, 0.03f, 0.065f, 0.1f, 0.17f, 0.52f, 0.58f, 0.66f, 1f)

        fun withAlpha(color: Int, alpha: Float): Int = (color and 0x00FFFFFF) or ((alpha * 255f).toInt() shl 24)
    }
}
