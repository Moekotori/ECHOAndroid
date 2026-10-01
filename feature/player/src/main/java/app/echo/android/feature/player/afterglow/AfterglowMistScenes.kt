package app.echo.android.feature.player.afterglow

import kotlin.math.pow
import kotlin.math.sin

/** Native port of echosteam's echoMistRidge / echoWisteria / echoLakeMist composition proportions.
 * Source: jizuraBackdropsEchoMist.js, revision 4857cc1d78901a6786073d4e2eb0c321169d5f01.
 * Static detail is flattened once; moving motes and water highlights belong to AfterglowBackdrop. */
internal fun paintAfterglowMist(p: AfterglowScenePaint, scene: AfterglowScene) {
    when (scene) {
        AfterglowScene.MistRidge -> mistRidge(p)
        AfterglowScene.Wisteria -> wisteria(p)
        AfterglowScene.LakeMist -> lakeMist(p)
        else -> error("Not a mist scene")
    }
}

private fun mistRidge(p: AfterglowScenePaint) = with(p) {
    val sky0 = mixAfterglow(palette.background, palette.accent, 0.2f)
    val sky1 = mixAfterglow(palette.background, AfterglowScenePaint.White, 0.5f)
    val sky2 = mixAfterglow(palette.background, palette.secondary, 0.36f)
    val fog = mixAfterglow(palette.background, AfterglowScenePaint.White, 0.7f)
    val far = mixAfterglow(palette.background, palette.accent, 0.3f)
    val near = mixAfterglow(palette.foreground, palette.background, 0.3f)
    sky(intArrayOf(sky0, sky1, sky2, fog), floatArrayOf(0f, 0.5f, 0.86f, 1f))
    val sunX = w * if (h > w) 0.66f else 0.3f + 0.42f * r(1)
    val sunY = h * if (h > w) 0.24f else 0.3f
    glow(sunX, sunY, u * 1.1f, 0xFFFFF3DC.toInt(), 178)
    circle(sunX, sunY, u * 0.105f, 0xFFFFF3DC.toInt(), 72)
    circle(sunX, sunY, u * 0.058f, AfterglowScenePaint.White, 242)
    val levels = floatArrayOf(0.5f, 0.58f, 0.66f, 0.75f, 0.84f, 0.95f)
    val amplitudes = floatArrayOf(0.16f, 0.15f, 0.16f, 0.15f, 0.13f, 0.1f)
    val depths = floatArrayOf(0f, 0.2f, 0.42f, 0.64f, 0.82f, 1f)
    for (i in levels.indices) {
        checkCancelled()
        val colour = mixAfterglow(far, near, depths[i].pow(1.15f))
        val foot = mixAfterglow(fog, colour, 0.25f + 0.5f * depths[i])
        ridge(h * levels[i], h * amplitudes[i], i * 7 + 1, colour, foot)
        if (i >= 4) repeat(20) { k ->
            val x = w * (k + r(k, i * 31) * 0.85f) / 20
            val y = h * levels[i] - h * amplitudes[i] * profile(x / w, i * 7 + 1)
            fir(x, y + u * 0.004f, u * (0.045f + 0.09f * r(k, i * 32).pow(1.5f)) * if (i == 5) 1.6f else 1f,
                mixAfterglow(colour, near, 0.25f))
        }
        if (i < 5) fog(h * (levels[i] + 0.02f), h * 0.11f, fog, 158, i * 13)
    }
}

private fun wisteria(p: AfterglowScenePaint) = with(p) {
    val top = mixAfterglow(palette.accent, 0xFF5B3AA8.toInt(), 0.4f)
    val tip = mixAfterglow(palette.accent, AfterglowScenePaint.White, 0.72f)
    sky(intArrayOf(mixAfterglow(palette.background, palette.accent, 0.12f),
        mixAfterglow(palette.background, AfterglowScenePaint.White, 0.55f),
        mixAfterglow(palette.background, palette.secondary, 0.25f)), floatArrayOf(0f, 0.6f, 1f))
    repeat(26) { i ->
        glow(r(i, 1) * w, h * (0.25f + 0.6f * r(i, 2)), u * (0.02f + 0.06f * r(i, 3)),
            if (i % 3 == 0) palette.accent else if (i % 3 == 1) palette.secondary else AfterglowScenePaint.White, 76)
    }
    val ground = h * 0.86f
    gradient(ground, h, intArrayOf(mixAfterglow(palette.background, palette.foreground, 0.1f),
        mixAfterglow(palette.background, palette.foreground, 0.24f)))
    canvas.drawRect(0f, ground, w, h, paint)
    repeat(70) { i -> oval(r(i, 9) * w, ground + (h - ground) * (0.1f + 0.9f * r(i, 10)),
        u * 0.005f, u * 0.0028f, mixAfterglow(tip, top, r(i, 12)), 215) }
    val wood = mixAfterglow(palette.foreground, palette.background, 0.3f)
    rect(0f, h * 0.05f, w, h * 0.05f + u * 0.05f, wood)
    rect(0f, h * 0.05f, w, h * 0.05f + u * 0.008f, AfterglowScenePaint.White, 30)
    repeat(9) { i ->
        val x = w * (i + 0.5f) / 9
        rect(x - u * 0.012f, 0f, x + u * 0.012f, h * 0.05f + u * 0.065f, wood)
    }
    val counts = intArrayOf(16, 11, 7)
    val lengths = floatArrayOf(0.19f, 0.3f, 0.44f)
    val widths = floatArrayOf(0.06f, 0.085f, 0.12f)
    val alphas = intArrayOf(140, 215, 255)
    for (depth in counts.indices) repeat(counts[depth]) { i ->
        checkCancelled()
        val x = w * (i + 0.5f + (r(i, depth * 31) - 0.5f) * 0.7f) / counts[depth]
        val length = u * lengths[depth] * (0.6f + 0.75f * r(i, depth * 32))
        val breadth = u * widths[depth] * (0.9f + 0.45f * r(i, depth * 33))
        line(x, h * 0.09f, x, h * 0.09f + length * 0.92f,
            mixAfterglow(top, 0xFF2E5A34.toInt(), 0.55f), alphas[depth], breadth * 0.03f)
        // Small petals keep the PC's tapered raceme silhouette; no per-petal gradient objects.
        repeat(26) { row ->
            val v = row / 26f
            val half = breadth * 0.52f * (1f - v * 0.95f).pow(0.6f) *
                (0.6f + 0.4f * sin(Math.PI.toFloat() * minOf(1f, v * 2.4f + 0.25f)))
            val count = 4 + (5 * (1f - v)).toInt()
            val colour = mixAfterglow(top, tip, v.pow(0.8f) * 0.9f + 0.1f * r(row, i))
            repeat(count) { k ->
                val px = x + ((k + 0.5f + (r(k, row + i * 71) - 0.5f) * 0.7f) / count * 2 - 1) * half
                val py = h * 0.09f + length * (0.02f + 0.92f * v) + (r(k, row + i * 73) - 0.5f) * length * 0.03f
                val size = breadth * (0.085f + 0.06f * (1f - v)) * (0.8f + 0.5f * r(k, row + i * 79))
                oval(px, py, size * 0.62f, size, colour, alphas[depth])
                oval(px - size * 0.18f, py - size * 0.22f, size * 0.24f, size * 0.35f,
                    mixAfterglow(colour, AfterglowScenePaint.White, 0.4f), alphas[depth] / 2)
            }
        }
    }
}

private fun lakeMist(p: AfterglowScenePaint) = with(p) {
    val horizon = h * 0.6f
    val sunX = w * if (h > w) 0.62f else 0.32f + 0.4f * r(2)
    val sunY = horizon - h * 0.17f
    val fog = mixAfterglow(palette.background, AfterglowScenePaint.White, 0.7f)
    val far = mixAfterglow(palette.background, palette.accent, 0.3f)
    val near = mixAfterglow(palette.foreground, palette.background, 0.3f)
    fun world() {
        sky(intArrayOf(mixAfterglow(palette.background, palette.accent, 0.2f),
            mixAfterglow(palette.background, AfterglowScenePaint.White, 0.5f),
            mixAfterglow(palette.background, palette.secondary, 0.36f)), floatArrayOf(0f, 0.6f, 1f), horizon)
        glow(sunX, sunY, u * 0.9f, 0xFFFFF3DC.toInt(), 190)
        circle(sunX, sunY, u * 0.052f, AfterglowScenePaint.White, 244)
        val depth = floatArrayOf(0.3f, 0.55f, 0.8f)
        val amplitude = floatArrayOf(0.4f, 0.3f, 0.2f)
        for (i in 0..2) {
            val colour = mixAfterglow(far, near, depth[i])
            ridge(horizon - u * 0.004f * i, horizon * amplitude[i], 20 + i * 3,
                colour, fog, horizon + 2)
        }
    }
    sky(intArrayOf(palette.background, far))
    world()
    canvas.save(); canvas.clipRect(0f, horizon, w, h)
    canvas.translate(0f, horizon * 2); canvas.scale(1f, -1f); world(); canvas.restore()
    gradient(horizon, h, intArrayOf(withAlpha(fog, 30), withAlpha(mixAfterglow(palette.accent, palette.foreground, 0.5f), 86)))
    canvas.drawRect(0f, horizon, w, h, paint)
    fog(horizon + u * 0.01f, u * 0.12f, fog, 105, 5, 8)
    fog(horizon + (h - horizon) * 0.55f, u * 0.1f, fog, 50, 9, 6)
    repeat(44) { i ->
        val left = i < 24
        val x = if (left) w * 0.13f * r(i, 61) else w * (0.86f + 0.14f * r(i, 61))
        val height = u * (0.09f + 0.16f * r(i, 62))
        val lean = (if (left) 1f else -1f) * u * (0.02f + 0.03f * r(i, 63))
        path.rewind(); path.moveTo(x, h + u * 0.01f)
        path.quadTo(x + lean * 0.3f, h - height * 0.6f, x + lean, h - height)
        paint.shader = null; paint.style = android.graphics.Paint.Style.STROKE
        paint.color = near; paint.alpha = 255; paint.strokeWidth = (u * 0.004f).coerceAtLeast(1f)
        canvas.drawPath(path, paint)
    }
}
