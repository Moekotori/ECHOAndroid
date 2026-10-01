package app.echo.android.feature.player.afterglow

import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.pow

/** Native composition port from echosteam jizuraBackdropsEchoNight.js (4857cc1d78901a6786073d4e2eb0c321169d5f01).
 * The dial and stars are rasterized once. Boats, twinkles and orbit planets remain bounded moving layers. */
internal fun paintAfterglowNight(p: AfterglowScenePaint, scene: AfterglowScene) {
    when (scene) {
        AfterglowScene.Voyage -> voyage(p)
        AfterglowScene.Aurora -> aurora(p)
        AfterglowScene.StarChart -> starChartGround(p)
        else -> error("Not a night scene")
    }
}

private fun nightSky(p: AfterglowScenePaint, bottom: Float) = with(p) {
    sky(intArrayOf(mixAfterglow(palette.background, 0xFF010208.toInt(), 0.6f), palette.background,
        mixAfterglow(palette.background, palette.secondary, 0.3f)), floatArrayOf(0f, 0.62f, 1f), bottom)
}

private fun milkyWay(p: AfterglowScenePaint, bottom: Float) = with(p) {
    val angle = -0.5f + 0.3f * r(90)
    val cx = w * 0.5f; val cy = bottom * 0.32f
    val reach = kotlin.math.hypot(w, h)
    repeat(9) { i ->
        val at = (i / 8f - 0.5f) * reach * 0.9f
        glow(cx + cos(angle) * at, cy + sin(angle) * at, u * (0.28f + 0.2f * r(i, 91)),
            if (i % 3 == 1) palette.accent else palette.secondary, 33)
    }
    repeat(1500) { i ->
        if (i % 64 == 0) checkCancelled()
        val at = (r(i, 1) - 0.5f) * reach * 0.95f
        val off = (r(i, 2) + r(i, 3) + r(i, 4) - 1.5f) * u * 0.32f
        val x = cx + cos(angle) * at - sin(angle) * off
        val y = cy + sin(angle) * at + cos(angle) * off
        if (y < bottom * 0.7f) {
            val alpha = ((0.1f + 0.55f * r(i, 5).pow(3)) * (1f - kotlin.math.abs(off) / (u * 0.5f)) * 255).toInt().coerceIn(0, 255)
            rect(x, y, x + 1, y + 1, AfterglowScenePaint.White, alpha)
        }
    }
    stars(140, bottom / h * 0.68f)
}

private fun voyage(p: AfterglowScenePaint) = with(p) {
    val horizon = h * 0.62f
    val moonX = w * if (h > w) 0.7f else 0.26f + 0.18f * r(1)
    val moonY = h * if (h > w) 0.2f else 0.22f
    val glow = mixAfterglow(palette.secondary, AfterglowScenePaint.White, 0.6f)
    val ink = mixAfterglow(palette.background, AfterglowScenePaint.Black, 0.78f)
    nightSky(p, horizon + 2); milkyWay(p, horizon)
    glow(moonX, moonY, u * 0.85f, glow, 105)
    circle(moonX, moonY, u * 0.06f, 0xFFFBFAF0.toInt())
    repeat(4) { i -> circle(moonX + (r(i, 41) - 0.5f) * u * 0.05f,
        moonY + (r(i, 43) - 0.5f) * u * 0.05f, u * (0.006f + r(i, 47) * 0.007f), 0xFFB8B8CC.toInt(), 102) }
    path.rewind(); path.moveTo(w * 0.62f, horizon + 1)
    path.quadTo(w * 0.74f, horizon - u * 0.055f, w * 0.88f, horizon + 1); path.close()
    fill(mixAfterglow(palette.background, ink, 0.7f))
    rect(w * 0.775f, horizon - u * 0.1f, w * 0.775f + u * 0.009f, horizon - u * 0.048f, ink)
    circle(w * 0.78f, horizon - u * 0.104f, u * 0.006f, palette.accent)
    gradient(horizon, h, intArrayOf(mixAfterglow(palette.background, 0xFF010208.toInt(), 0.35f),
        mixAfterglow(palette.background, 0xFF010208.toInt(), 0.7f)))
    canvas.drawRect(0f, horizon, w, h, paint)
    repeat(46) { i ->
        val v = i / 46f
        val y = horizon + (h - horizon) * (0.02f + 0.9f * v)
        val half = u * (0.008f + 0.11f * v) * (0.4f + r(i, 8))
        rect(moonX - half + (r(i, 10) - 0.5f) * u * 0.05f, y, moonX + half, y + (u * 0.003f).coerceAtLeast(1f),
            glow, (50 * (1f - v * 0.45f)).toInt())
    }
}

private fun aurora(p: AfterglowScenePaint) = with(p) {
    val horizon = h * 0.68f
    nightSky(p, h); stars(150, 0.56f)
    val greens = intArrayOf(mixAfterglow(0xFF38E8B8.toInt(), palette.secondary, 0.35f),
        mixAfterglow(0xFF76C8FF.toInt(), palette.secondary, 0.35f), mixAfterglow(0xFFCA8AFF.toInt(), palette.accent, 0.25f))
    fun curtains(reflection: Boolean) {
        for (k in 0..2) {
            checkCancelled()
            for (i in 0..95) {
                val fraction = i / 96f
                val wave = sin(fraction * 5 + k * 2 + r(k, 1) * 6) + 0.6f * sin(fraction * 11 + k)
                val base = h * (0.44f + (k - 1) * 0.07f + wave * 0.028f)
                val height = h * (0.2f + 0.12f * (0.5f + 0.5f * sin(fraction * 3.2f + k * 1.7f))) * (1f - 0.14f * k)
                val alpha = ((if (reflection) 70 else 180) * (0.55f + 0.45f * sin(fraction * 4.7f + k * 3))).toInt().coerceIn(0, 255)
                gradient(base - height, base, intArrayOf(greens[k] and 0x00FFFFFF, withAlpha(greens[k], alpha / 3),
                    withAlpha(greens[k], alpha), withAlpha(mixAfterglow(greens[k], AfterglowScenePaint.White, 0.5f), alpha), greens[k] and 0x00FFFFFF),
                    floatArrayOf(0f, 0.35f, 0.78f, 0.95f, 1f))
                canvas.drawRect(fraction * w, base - height, (fraction + 1 / 96f) * w + 0.5f, base, paint)
            }
        }
    }
    curtains(false)
    gradient(horizon, h, intArrayOf(mixAfterglow(palette.background, greens[0], 0.16f),
        mixAfterglow(palette.background, AfterglowScenePaint.Black, 0.6f)))
    canvas.drawRect(0f, horizon, w, h, paint)
    canvas.save(); canvas.clipRect(0f, horizon, w, h); canvas.translate(0f, horizon * 1.62f + u * 0.02f)
    canvas.scale(1f, -0.62f); curtains(true); canvas.restore()
    val ink = mixAfterglow(palette.background, AfterglowScenePaint.Black, 0.78f)
    repeat(34) { i ->
        val x = w * (i + r(i, 41) * 0.8f) / 34
        val tall = u * (0.06f + 0.16f * r(i, 42).pow(1.6f)) * if (x < w * 0.3f || x > w * 0.72f) 1.25f else 0.6f
        fir(x, horizon + u * 0.012f, tall, ink)
    }
    val cx = w * 0.56f
    rect(cx - u * 0.035f, horizon - u * 0.03f, cx + u * 0.035f, horizon, ink)
    path.rewind(); path.moveTo(cx - u * 0.042f, horizon - u * 0.03f)
    path.lineTo(cx, horizon - u * 0.058f); path.lineTo(cx + u * 0.042f, horizon - u * 0.03f); path.close(); fill(ink)
    val lamp = mixAfterglow(palette.accent, 0xFFFFE9B0.toInt(), 0.45f)
    rect(cx - u * 0.018f, horizon - u * 0.022f, cx - u * 0.006f, horizon - u * 0.008f, lamp)
    rect(cx + u * 0.006f, horizon - u * 0.022f, cx + u * 0.018f, horizon - u * 0.008f, lamp)
    glow(cx, horizon - u * 0.012f, u * 0.09f, lamp, 100)
}

private fun starChartGround(p: AfterglowScenePaint) = with(p) {
    sky(intArrayOf(mixAfterglow(palette.background, 0xFF010208.toInt(), 0.6f),
        mixAfterglow(palette.background, 0xFF010208.toInt(), 0.24f)))
    glow(w * if (h > w) 0.5f else 0.62f, h * if (h > w) 0.4f else 0.5f, u * 0.69f, palette.secondary, 55)
    stars(200, 1f)
    val colour = mixAfterglow(palette.secondary, AfterglowScenePaint.White, 0.4f)
    repeat((w / (u * 0.06f)).toInt() + 1) { i -> line(i * u * 0.06f, 0f, i * u * 0.06f, h, colour, 15, 1f) }
    repeat((h / (u * 0.06f)).toInt() + 1) { i -> line(0f, i * u * 0.06f, w, i * u * 0.06f, colour, 15, 1f) }
    val x = w * if (h > w) 0.18f else 0.16f; val y = h * if (h > w) 0.82f else 0.78f; val radius = u * 0.15f
    canvas.save(); canvas.rotate(-23f, x, y)
    paint.shader = null; paint.color = colour; paint.alpha = 150; paint.style = Paint.Style.STROKE; paint.strokeWidth = radius * 0.14f
    canvas.drawOval(x - radius * 1.75f, y - radius * 0.42f, x + radius * 1.75f, y + radius * 0.42f, paint)
    circle(x, y, radius, mixAfterglow(palette.accent, palette.background, 0.25f))
    glow(x - radius * 0.4f, y - radius * 0.4f, radius, palette.accent, 125)
    canvas.restore()
}

internal fun paintAfterglowDial(p: AfterglowScenePaint) = with(p) {
    val cx = w * 0.5f; val cy = h * 0.5f; val radius = u * 0.47f
    val colour = mixAfterglow(palette.secondary, AfterglowScenePaint.White, 0.4f)
    paint.shader = null; paint.color = colour; paint.alpha = 140; paint.style = Paint.Style.STROKE; paint.strokeWidth = (u * 0.002f).coerceAtLeast(0.7f)
    for (fraction in floatArrayOf(1f, 0.94f, 0.62f, 0.3f)) canvas.drawCircle(cx, cy, radius * fraction, paint)
    repeat(120) { i ->
        val angle = i / 120f * (2 * PI).toFloat()
        val length = if (i % 10 == 0) 0.045f else if (i % 5 == 0) 0.03f else 0.016f
        line(cx + cos(angle) * radius, cy + sin(angle) * radius, cx + cos(angle) * radius * (1f - length),
            cy + sin(angle) * radius * (1f - length), colour, if (i % 10 == 0) 200 else 114, u * 0.0015f)
    }
    paint.shader = null; paint.style = Paint.Style.FILL; paint.color = colour; paint.alpha = 200
    paint.textSize = u * 0.02f; paint.typeface = Typeface.MONOSPACE; paint.textAlign = Paint.Align.CENTER
    repeat(12) { i ->
        val angle = (i / 12f * (2 * PI) - PI / 2).toFloat()
        canvas.drawText((i * 30).toString().padStart(3, '0'), cx + cos(angle) * radius * 0.89f,
            cy + sin(angle) * radius * 0.89f + paint.textSize * 0.3f, paint)
    }
    repeat(9) { constellation ->
        checkCancelled()
        val x = cx + (r(constellation, 1) * 2 - 1) * radius * 0.65f
        val y = cy + (r(constellation, 2) * 2 - 1) * radius * 0.65f
        var lastX = x; var lastY = y
        repeat(4 + (afterglowComposition(constellation, seed) % 4)) { i ->
            val px = x + (r(i, constellation * 7 + 4) - 0.5f) * radius * 0.4f
            val py = y + (r(i, constellation * 7 + 5) - 0.5f) * radius * 0.4f
            if (i > 0) line(lastX, lastY, px, py, colour, 140, u * 0.0016f)
            glow(px, py, u * 0.025f, if (constellation % 3 == 0) palette.accent else AfterglowScenePaint.White, 70)
            circle(px, py, u * (0.0035f + 0.005f * r(i, constellation + 6)), AfterglowScenePaint.White)
            lastX = px; lastY = py
        }
    }
}
