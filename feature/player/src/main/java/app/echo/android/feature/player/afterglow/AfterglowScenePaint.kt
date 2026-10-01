package app.echo.android.feature.player.afterglow

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Raster preparation only. These helpers are never called from an animated frame. */
internal class AfterglowScenePaint(
    val canvas: Canvas, val w: Float, val h: Float, val palette: AfterglowPalette,
    val seed: Int, val checkCancelled: () -> Unit,
) {
    val u = minOf(w, h)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val path = Path()
    fun r(index: Int, salt: Int = 0): Float = afterglowComposition(index, seed xor salt) / Int.MAX_VALUE.toFloat()
    fun rect(left: Float, top: Float, right: Float, bottom: Float, colour: Int, alpha: Int = 255) {
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = colour; paint.alpha = alpha
        canvas.drawRect(left, top, right, bottom, paint)
    }
    fun circle(x: Float, y: Float, radius: Float, colour: Int, alpha: Int = 255) {
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = colour; paint.alpha = alpha
        canvas.drawCircle(x, y, radius.coerceAtLeast(0.1f), paint)
    }
    fun oval(x: Float, y: Float, rx: Float, ry: Float, colour: Int, alpha: Int = 255) {
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = colour; paint.alpha = alpha
        canvas.drawOval(x - rx, y - ry, x + rx, y + ry, paint)
    }
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, colour: Int, alpha: Int, stroke: Float) {
        paint.shader = null; paint.style = Paint.Style.STROKE; paint.color = colour; paint.alpha = alpha
        paint.strokeWidth = stroke.coerceAtLeast(0.6f); canvas.drawLine(x0, y0, x1, y1, paint)
    }
    fun fill(colour: Int, alpha: Int = 255) {
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = colour; paint.alpha = alpha
        canvas.drawPath(path, paint)
    }
    fun gradient(y0: Float, y1: Float, colours: IntArray, stops: FloatArray? = null) {
        paint.style = Paint.Style.FILL; paint.alpha = 255
        paint.shader = LinearGradient(0f, y0, 0f, y1.coerceAtLeast(y0 + 1f), colours, stops, Shader.TileMode.CLAMP)
    }
    fun sky(colours: IntArray, stops: FloatArray? = null, bottom: Float = h) {
        gradient(0f, bottom, colours, stops); canvas.drawRect(0f, 0f, w, bottom, paint); paint.shader = null
    }
    fun glow(x: Float, y: Float, radius: Float, colour: Int, alpha: Int) {
        paint.style = Paint.Style.FILL; paint.alpha = 255
        paint.shader = RadialGradient(x, y, radius.coerceAtLeast(1f),
            intArrayOf(withAlpha(colour, alpha), withAlpha(colour, alpha / 3), colour and 0x00FFFFFF),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(x - radius, y - radius, x + radius, y + radius, paint); paint.shader = null
    }
    fun stars(count: Int, limit: Float = 0.6f) {
        repeat(count) { i ->
            if (i % 64 == 0) checkCancelled()
            val y = r(i, 2) * h * limit
            circle(r(i, 1) * w, y, u * (0.0008f + 0.0016f * r(i, 4) * r(i, 4)),
                White, ((0.25f + 0.7f * r(i, 3) * r(i, 3)) * (1f - y / (h * (limit + 0.15f))) * 255).toInt())
        }
    }
    fun ridge(base: Float, amplitude: Float, salt: Int, colour: Int, fog: Int, bottom: Float = h) {
        path.rewind(); path.moveTo(-2f, bottom + 2f)
        var top = bottom
        for (i in 0..96) {
            val x = w * i / 96
            val y = base - amplitude * profile(i / 96f, salt)
            path.lineTo(x, y); top = minOf(top, y)
        }
        path.lineTo(w + 2f, bottom + 2f); path.close()
        gradient(top, bottom, intArrayOf(colour, mixAfterglow(colour, fog, 0.5f), fog), floatArrayOf(0f, 0.55f, 1f))
        canvas.drawPath(path, paint); paint.shader = null
    }
    fun profile(fraction: Float, salt: Int): Float {
        val theta = fraction * (2 * PI).toFloat()
        return (0.5f + 0.5f * (sin(theta + r(salt, 1) * 6.28f) * 0.5f +
            sin(theta * 2 + r(salt, 2) * 6.28f) * 0.3f + sin(theta * 5 + r(salt, 3) * 6.28f) * 0.14f +
            sin(theta * 12 + r(salt, 4) * 6.28f) * 0.06f)).coerceIn(0f, 1f)
    }
    fun fir(x: Float, base: Float, height: Float, colour: Int) {
        repeat(6) { tier ->
            val y = base - height * (1f - tier / 6f * 0.94f)
            val half = height * 0.17f * (0.25f + 0.75f * (tier + 1) / 6)
            path.rewind(); path.moveTo(x, y - height * 0.05f)
            path.quadTo(x + half * 0.55f, y + height * 0.02f, x + half, y + height * 0.08f)
            path.lineTo(x - half, y + height * 0.08f); path.close(); fill(colour)
        }
        rect(x - height * 0.012f, base - height * 0.06f, x + height * 0.012f, base + height * 0.02f, colour)
    }
    fun fog(y: Float, band: Float, colour: Int, alpha: Int, salt: Int, count: Int = 7) {
        repeat(count) { i ->
            val x = w * (i + r(i, salt) * 0.8f) / count
            val cy = y + (r(i, salt + 1) - 0.5f) * band
            val rw = w * (0.22f + 0.26f * r(i, salt + 2))
            val rh = band * (0.35f + 0.5f * r(i, salt + 3))
            canvas.save(); canvas.translate(x, cy); canvas.scale(1f, rh / rw)
            glow(0f, 0f, rw * 0.5f, colour, alpha); canvas.restore()
        }
    }
    fun star(x: Float, y: Float, radius: Float, colour: Int, alpha: Int) {
        path.rewind()
        repeat(8) { i ->
            val angle = (i * PI / 4 - PI / 2).toFloat()
            val rr = radius * if (i % 2 == 0) 1f else 0.16f
            val px = x + cos(angle) * rr; val py = y + sin(angle) * rr
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close(); fill(colour, alpha)
    }
    companion object { val White = 0xFFFFFFFF.toInt(); val Black = 0xFF000000.toInt() }
}

internal fun mixAfterglow(a: Int, b: Int, amount: Float): Int {
    fun component(shift: Int): Int = ((a ushr shift and 255) * (1f - amount) + (b ushr shift and 255) * amount).toInt().coerceIn(0, 255)
    return 0xFF000000.toInt() or (component(16) shl 16) or (component(8) shl 8) or component(0)
}
internal fun withAlpha(colour: Int, alpha: Int): Int = (colour and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
