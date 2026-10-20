package app.echo.android.feature.player

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import app.echo.android.design.ArtworkColorGrid
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * An isometric field of columns. Each column takes the cover's color at its position and rises with the
 * cover's brightness there, so bright parts of the cover stand up as towers. Columns breathe slowly, and every
 * lyric [pulse] sends a swell outward from behind the cover. Geometry and colors are built once per size;
 * a frame only does arithmetic and reuses one Path and one Paint. Faces are filled only: edge strokes doubled
 * the frame time on an emulator, and the three shades already separate the faces.
 */
internal class IsoBackdropField(deep: Color, private val grid: ArtworkColorGrid, dark: Boolean) : BackdropField {
    private val base = Paint().apply {
        color = lerp(deep, if (dark) Color.Black else Color.White, if (dark) 0.45f else 0.2f).toArgb()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val face = Path()
    private var cubes: List<Cube> = emptyList()
    private var laidOutWidth = 0f
    private var laidOutHeight = 0f
    private var halfWidth = 0f
    private var halfHeight = 0f
    private var unitHeight = 0f

    override fun draw(canvas: Canvas, width: Float, height: Float, elapsedMs: Long, opacity: Float, pulse: Float) {
        if (width <= 0f || height <= 0f || opacity <= 0f) return
        if (width != laidOutWidth || height != laidOutHeight) layout(width, height)
        val alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        base.alpha = alpha
        canvas.drawRect(0f, 0f, width, height, base)
        val time = (elapsedMs + BackdropStillOffsetMs) / 1000f
        val kick = pulse.coerceIn(0f, 1f)
        val originX = width * 0.5f
        val originY = height * 0.34f
        val waveRadius = (1f - kick) * height * 1.1f
        val sigma = height * 0.0885f
        val columns = cubes
        for (index in columns.indices) {
            val cube = columns[index]
            var swell = 0f
            if (kick > 0.01f) {
                val e = (hypot(cube.x - originX, cube.y - originY) - waveRadius) / sigma
                swell = kick * exp(-e * e)
            }
            val rise = unitHeight * (0.12f + 1.5f * cube.lift) * cube.scale * (0.94f + 0.08f * sin(time * 0.6f + cube.phase)) +
                swell * unitHeight * 0.9f
            val x = cube.x
            val topY = cube.y - rise
            val bottomY = cube.y
            face.rewind()
            face.moveTo(x - halfWidth, topY)
            face.lineTo(x, topY + halfHeight)
            face.lineTo(x, bottomY + halfHeight)
            face.lineTo(x - halfWidth, bottomY)
            face.close()
            fill.color = cube.left
            fill.alpha = alpha
            canvas.drawPath(face, fill)
            face.rewind()
            face.moveTo(x + halfWidth, topY)
            face.lineTo(x, topY + halfHeight)
            face.lineTo(x, bottomY + halfHeight)
            face.lineTo(x + halfWidth, bottomY)
            face.close()
            fill.color = cube.right
            fill.alpha = alpha
            canvas.drawPath(face, fill)
            face.rewind()
            face.moveTo(x, topY - halfHeight)
            face.lineTo(x + halfWidth, topY)
            face.lineTo(x, topY + halfHeight)
            face.lineTo(x - halfWidth, topY)
            face.close()
            fill.color = if (swell > 0.02f) blend(cube.top, cube.topLit, min(1f, swell)) else cube.top
            fill.alpha = alpha
            canvas.drawPath(face, fill)
        }
    }

    /** Builds the visible lattice for this size, ordered back to front so nearer columns cover farther ones. */
    private fun layout(width: Float, height: Float) {
        laidOutWidth = width
        laidOutHeight = height
        val tileWidth = width / Columns
        halfWidth = tileWidth / 2f
        halfHeight = tileWidth / 4f
        unitHeight = tileWidth * 1.05f
        val random = Random(8)
        val built = ArrayList<Cube>(320)
        for (i in -8 until Extent) for (j in -8 until Extent) {
            val x = width / 2f + (i - j) * halfWidth
            val y = -unitHeight + (i + j) * halfHeight
            if (x < -tileWidth || x > width + tileWidth || y < -unitHeight * 1.2f || y > height + halfHeight * 2f) continue
            val color = grid.sample((x / width).coerceIn(0f, 1f), (y / height).coerceIn(0f, 1f))
            built += Cube(x, y, i, j, color, luminance(color), 0.85f + random.nextFloat() * 0.3f)
        }
        val low = built.minOfOrNull { it.luminance } ?: 0f
        val span = max(0.001f, (built.maxOfOrNull { it.luminance } ?: 1f) - low)
        built.sortWith(compareBy<Cube> { it.i + it.j }.thenBy { it.i })
        built.forEach { it.finish((it.luminance - low) / span) }
        cubes = built
    }

    private class Cube(val x: Float, val y: Float, val i: Int, val j: Int, color: Int, val luminance: Float, val scale: Float) {
        val phase = i * 0.5f + j * 0.3f
        val left = shade(color, 0.7f)
        val right = shade(color, 0.46f)
        val top = shade(color, 1.14f)
        val topLit = shade(color, 1.44f)
        var lift = 0f
            private set

        fun finish(normalized: Float) {
            lift = normalized.coerceIn(0f, 1f).pow(1.3f)
        }
    }

    private companion object {
        const val Columns = 5
        const val Extent = 40

        fun luminance(color: Int): Float =
            (0.2126f * (color shr 16 and 0xFF) + 0.7152f * (color shr 8 and 0xFF) + 0.0722f * (color and 0xFF)) / 255f

        fun shade(color: Int, factor: Float): Int {
            val r = ((color shr 16 and 0xFF) * factor).toInt().coerceIn(0, 255)
            val g = ((color shr 8 and 0xFF) * factor).toInt().coerceIn(0, 255)
            val b = ((color and 0xFF) * factor).toInt().coerceIn(0, 255)
            return 0xFF shl 24 or (r shl 16) or (g shl 8) or b
        }

        fun blend(from: Int, to: Int, amount: Float): Int {
            fun channel(shift: Int): Int {
                val a = from shr shift and 0xFF
                val b = to shr shift and 0xFF
                return (a + (b - a) * amount).toInt()
            }
            return 0xFF shl 24 or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
    }
}
