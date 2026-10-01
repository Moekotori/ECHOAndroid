package app.echo.android.feature.player.afterglow

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/** Prepare on Default. Frames only submit bounded cached images and a fixed moving inventory. */
internal class AfterglowBackdrop(
    val width: Float, val height: Float, val palette: AfterglowPalette,
    night: Boolean, composition: Int, private val particles: Int,
    val scene: AfterglowScene = if (night) arrayOf(AfterglowScene.Voyage, AfterglowScene.Aurora, AfterglowScene.StarChart)[composition % 3]
        else arrayOf(AfterglowScene.MistRidge, AfterglowScene.Wisteria, AfterglowScene.LakeMist)[composition % 3],
    maxSide: Int = 540, checkCancelled: () -> Unit = {},
) {
    private val u = minOf(width, height)
    private val size = afterglowRasterSize(width.toInt(), height.toInt(), maxSide, scene)
    private val background = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    private val dial = if (size.dial > 0) Bitmap.createBitmap(size.dial, size.dial, Bitmap.Config.ARGB_8888) else null
    private val boat = when (scene) {
        AfterglowScene.Voyage -> Bitmap.createBitmap(112, 84, Bitmap.Config.ARGB_8888)
        AfterglowScene.LakeMist -> Bitmap.createBitmap(224, 90, Bitmap.Config.ARGB_8888)
        else -> null
    }
    val cacheBytes: Int get() = background.allocationByteCount + (dial?.allocationByteCount ?: 0) + (boat?.allocationByteCount ?: 0)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val backdropBounds = RectF(0f, 0f, width, height)
    private val dialX = width * if (height > width) 0.5f else 0.62f
    private val dialY = height * if (height > width) 0.4f else 0.5f
    private val dialBounds = RectF(dialX - u * 0.49f, dialY - u * 0.49f, dialX + u * 0.49f, dialY + u * 0.49f)
    private val boatBounds = if (scene == AfterglowScene.Voyage) RectF(-56f, -76f, 56f, 8f) else RectF(-112f, -80f, 112f, 10f)
    private val phase = FloatArray(particles) { afterglowComposition(it, composition) / Int.MAX_VALUE.toFloat() }
    private val x = FloatArray(particles) { afterglowComposition(it + 17, composition) / Int.MAX_VALUE.toFloat() }
    private val y = FloatArray(particles) { afterglowComposition(it + 33, composition) / Int.MAX_VALUE.toFloat() }
    private val orbitK = floatArrayOf(0.62f, 0.8f, 0.45f)
    private val orbitSpeed = floatArrayOf(0.6f, 0.4f, 0.9f)
    private val orbitTilt = floatArrayOf(0.22f, 0.3f, -0.3f)

    init {
        try {
            checkCancelled()
            val p = AfterglowScenePaint(Canvas(background), size.width.toFloat(), size.height.toFloat(), palette, composition, checkCancelled)
            if (scene.night) paintAfterglowNight(p, scene) else paintAfterglowMist(p, scene)
            checkCancelled()
            dial?.let { paintAfterglowDial(AfterglowScenePaint(Canvas(it), it.width.toFloat(), it.height.toFloat(), palette, composition, checkCancelled)) }
            boat?.let { paintBoat(AfterglowScenePaint(Canvas(it), it.width.toFloat(), it.height.toFloat(), palette, composition, checkCancelled), scene == AfterglowScene.Voyage) }
            checkCancelled()
        } catch (error: Throwable) {
            // Never-published images have no UI/GPU owner and can be freed immediately on cancellation.
            background.recycle(); dial?.recycle(); boat?.recycle()
            throw error
        }
    }

    fun draw(canvas: Canvas, seconds: Float, motion: Boolean) {
        paint.alpha = 255
        canvas.drawBitmap(background, null, backdropBounds, paint)
        val t = if (motion) seconds else 0f
        dial?.let {
            canvas.save(); canvas.rotate(t * 0.7f, dialX, dialY)
            canvas.drawBitmap(it, null, dialBounds, paint); canvas.restore()
            for (i in orbitK.indices) {
                val angle = t * orbitSpeed[i] * 0.3f + i * 2
                canvas.save(); canvas.rotate(orbitTilt[i] * 57.29578f, dialX, dialY)
                val px = dialX + cos(angle) * u * 0.46f * orbitK[i] * 1.05f
                val py = dialY + sin(angle) * u * 0.46f * orbitK[i] * 0.42f
                paint.color = if (i == 1) palette.secondary else palette.accent; paint.alpha = 45
                canvas.drawCircle(px, py, u * 0.022f, paint)
                paint.alpha = 240; canvas.drawCircle(px, py, u * 0.008f, paint); canvas.restore()
            }
        }
        boat?.let {
            val steamer = scene == AfterglowScene.Voyage
            val base = height * if (steamer) 0.62f else 0.6f
            val px = if (steamer) width * (0.1f + wrap(t / 70f, 1f) * 0.85f) else width * 0.4f + sin(t * 0.12f) * u * 0.04f
            val py = if (steamer) base + u * 0.02f + sin(t * 1.2f) * u * 0.002f else base + (height - base) * 0.2f + sin(t * 1.1f) * u * 0.002f
            val scale = u * (if (steamer) 0.3f else 0.11f) / 100f
            canvas.save(); canvas.translate(px, py); canvas.scale(scale, scale)
            paint.alpha = 255; canvas.drawBitmap(it, null, boatBounds, paint); canvas.restore()
            canvas.save(); canvas.translate(px, py + u * 0.005f); canvas.scale(scale, -scale * 0.5f)
            paint.alpha = 70; canvas.drawBitmap(it, null, boatBounds, paint); canvas.restore()
        }
        for (i in phase.indices) {
            paint.color = if (scene.night) AfterglowScenePaint.White else if (i % 3 == 0) palette.accent else palette.secondary
            if (scene == AfterglowScene.Wisteria) {
                val life = wrap(t * (0.035f + 0.03f * phase[i]) + y[i], 1f)
                val px = x[i] * width + sin(t * 0.7f + i * 2) * u * 0.05f + life * u * 0.08f
                val py = -u * 0.02f + life * height * 1.05f
                paint.alpha = (210 * sin(Math.PI.toFloat() * life)).toInt().coerceIn(0, 210)
                canvas.save(); canvas.rotate(t * 35f + i * 15, px, py)
                canvas.drawOval(px - u * 0.007f, py - u * 0.0035f, px + u * 0.007f, py + u * 0.0035f, paint); canvas.restore()
            } else {
                val px = x[i] * width + (if (scene.night) 0f else sin(t * 0.4f + i) * u * 0.03f)
                val py = if (scene.night) y[i] * height * 0.55f else height - wrap(y[i] * height + t * height * (0.01f + 0.02f * phase[i]), height)
                paint.alpha = (if (scene.night) 65 + 150 * (0.5f + 0.5f * sin(t * (0.7f + phase[i]) + i * 2.3f))
                    else 40 + 90 * (0.5f + 0.5f * sin(t * 0.9f + i * 2))).toInt()
                canvas.drawCircle(px, py, u * (if (scene.night) 0.0025f else 0.0035f), paint)
            }
        }
        paint.alpha = 255
    }
}

private fun wrap(value: Float, span: Float): Float = ((value % span) + span) % span

private fun paintBoat(p: AfterglowScenePaint, steamer: Boolean) = with(p) {
    canvas.translate(if (steamer) 56f else 112f, if (steamer) 76f else 80f)
    val ink = mixAfterglow(palette.foreground, palette.background, if (steamer) 0.88f else 0.15f)
    val lamp = if (steamer) mixAfterglow(palette.accent, 0xFFFFE9B0.toInt(), 0.45f) else 0xFFFFE2A0.toInt()
    path.rewind()
    if (steamer) {
        path.moveTo(-50f, -13f); path.lineTo(56f, -13f); path.lineTo(46f, 0f); path.lineTo(-40f, 0f); path.close(); fill(ink)
        rect(-30f, -25f, 30f, -13f, ink); rect(-18f, -36f, 16f, -25f, ink)
        rect(-2f, -43f, 12f, -36f, ink); rect(-15f, -56f, -8f, -36f, ink)
        rect(34f, -66f, 35.2f, -13f, ink); rect(-44f, -50f, -43f, -13f, ink)
        line(34f, -64f, -44f, -50f, ink, 255, 0.6f)
        repeat(11) { i -> rect(-27f + i * 5, -22f, -24.8f + i * 5, -18.8f, lamp) }
        repeat(6) { i -> rect(-15f + i * 5, -33f, -13f + i * 5, -30f, lamp) }
        repeat(10) { i -> circle(-36f + i * 9, -7.5f, 1.1f, lamp) }
    } else {
        path.moveTo(-100f, -8f); path.quadTo(0f, 24f, 100f, -14f); path.lineTo(86f, -2f)
        path.quadTo(0f, 16f, -90f, 0f); path.close(); fill(ink)
        rect(2f, -46f, 6f, -6f, ink)
        path.rewind(); path.moveTo(6f, -44f); path.lineTo(50f, -12f); path.lineTo(6f, -12f); path.close(); fill(ink)
        glow(-90f, -20f, 20f, lamp, 120); circle(-90f, -20f, 4.5f, lamp)
    }
}
