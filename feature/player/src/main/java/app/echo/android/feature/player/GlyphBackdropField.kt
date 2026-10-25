package app.echo.android.feature.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.echo.android.design.ArtworkColorGrid
import kotlin.math.ceil
import kotlin.math.pow

/**
 * The cover typeset as a field of characters: dense glyphs where it is bright, sparse ones where it is dark,
 * each in the cover's own color. The field is drawn into one bitmap per size (about 3,000 glyphs, once);
 * a frame draws that bitmap and a pale band that sweeps down.
 */
internal class GlyphBackdropField(private val deep: Color, private val grid: ArtworkColorGrid) : BackdropField {
    private val base = Paint().apply { color = deep.toArgb() }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val bandPaint = Paint()
    private var field: Bitmap? = null
    private var laidOutWidth = 0f
    private var laidOutHeight = 0f

    override fun draw(canvas: Canvas, width: Float, height: Float, elapsedMs: Long, opacity: Float, pulse: Float) {
        if (width <= 0f || height <= 0f || opacity <= 0f) return
        if (width != laidOutWidth || height != laidOutHeight || field == null) layout(width, height)
        val alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        base.alpha = alpha
        canvas.drawRect(0f, 0f, width, height, base)
        bitmapPaint.alpha = alpha
        field?.let { canvas.drawBitmap(it, 0f, 0f, bitmapPaint) }
        val reach = height * 0.07f
        val y = (((elapsedMs + BackdropStillOffsetMs) / 1000f * 70f * height / 740f) % (height + 2 * reach)) - reach
        bandPaint.alpha = alpha
        canvas.save()
        canvas.translate(0f, y)
        canvas.drawRect(0f, -reach, width, reach, bandPaint)
        canvas.restore()
    }

    private fun layout(width: Float, height: Float) {
        laidOutWidth = width
        laidOutHeight = height
        field?.recycle()
        val bitmap = Bitmap.createBitmap(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val target = Canvas(bitmap)
        val cellWidth = width / Columns
        val cellHeight = cellWidth * 10f / 9f
        val rows = ceil(height / cellHeight).toInt()
        val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = cellHeight * 1.1f
        }
        var low = Float.MAX_VALUE
        var high = -Float.MAX_VALUE
        val colors = IntArray(rows * Columns)
        val levels = FloatArray(rows * Columns)
        for (row in 0 until rows) {
            val v = ((row + 0.5f) * cellHeight / height).coerceIn(0f, 0.999f)
            for (column in 0 until Columns) {
                val color = grid.sample((column + 0.5f) / Columns, v)
                val value = (0.2126f * (color shr 16 and 0xFF) + 0.7152f * (color shr 8 and 0xFF) + 0.0722f * (color and 0xFF)) / 255f
                colors[row * Columns + column] = color
                levels[row * Columns + column] = value
                if (value < low) low = value
                if (value > high) high = value
            }
        }
        val span = (high - low).coerceAtLeast(0.02f)
        for (row in 0 until rows) for (column in 0 until Columns) {
            val index = row * Columns + column
            val level = ((levels[index] - low) / span).coerceIn(0f, 1f).pow(0.8f)
            val character = Ramp[(level * Ramp.length).toInt().coerceAtMost(Ramp.length - 1)]
            if (character == ' ') continue
            glyph.color = lifted(colors[index])
            target.drawText(character.toString(), column * cellWidth, row * cellHeight + cellHeight * 0.85f, glyph)
        }
        field = bitmap
        val reach = height * 0.07f
        bandPaint.shader = LinearGradient(0f, -reach, 0f, 0f, intArrayOf(0x00FFFFFF, 0x24FFFFFF), null, Shader.TileMode.CLAMP)
    }

    /** Keeps dark cover colors readable on the dark page. */
    private fun lifted(color: Int): Int {
        val r = color shr 16 and 0xFF
        val g = color shr 8 and 0xFF
        val b = color and 0xFF
        val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
        val t = ((0.5f - lum) * 1.6f).coerceIn(0f, 0.8f)
        return 0xFF shl 24 or ((r + (255 - r) * t).toInt() shl 16) or ((g + (255 - g) * t).toInt() shl 8) or (b + (255 - b) * t).toInt()
    }

    private companion object {
        const val Columns = 40
        const val Ramp = " .,:;i1tfLCG08@"
    }
}
