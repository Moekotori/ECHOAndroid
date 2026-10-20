package app.echo.android.feature.player

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.settings.EchoLyricsWordHighlightStyle as Style

internal fun lyricsWordStyleLabel(style: Style): Int = when (style) {
    Style.Sweep -> R.string.lyrics_word_style_sweep
    Style.Fade -> R.string.lyrics_word_style_fade
    Style.Lift -> R.string.lyrics_word_style_lift
    Style.Hop -> R.string.lyrics_word_style_hop
    Style.Swell -> R.string.lyrics_word_style_swell
    Style.Halo -> R.string.lyrics_word_style_halo
    Style.Spot -> R.string.lyrics_word_style_spot
    Style.Wave -> R.string.lyrics_word_style_wave
    Style.Lens -> R.string.lyrics_word_style_lens
    Style.Flip -> R.string.lyrics_word_style_flip
    Style.Spark -> R.string.lyrics_word_style_spark
    Style.Scan -> R.string.lyrics_word_style_scan
    Style.Underline -> R.string.lyrics_word_style_underline
    Style.Dots -> R.string.lyrics_word_style_dots
}

/** One static miniature per style: a finished letter, the letter being sung, and two still waiting. */
@Composable
internal fun lyricsWordStyleOptions(): List<SettingsOption> =
    Style.entries.map { style ->
        SettingsOption(style.id, stringResource(lyricsWordStyleLabel(style))) { accent -> drawWordStyle(style, accent) }
    }

private const val SampleText = "ECHO"
private const val Current = 1

private fun DrawScope.drawWordStyle(style: Style, accent: Color) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1D1B25), Color(0xFF121116))))
    val em = size.height * 0.46f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = em
    }
    val count = SampleText.length
    val gap = em * 0.12f
    val widths = FloatArray(count) { paint.measureText(SampleText, it, it + 1) }
    val lefts = FloatArray(count)
    var x = (size.width - widths.sum() - gap * (count - 1)) / 2f
    for (i in 0 until count) { lefts[i] = x; x += widths[i] + gap }
    val baseline = size.height / 2f + em * 0.36f
    val dim = Color.White.copy(alpha = 0.26f)

    // Per-letter look: how lit it is, vertical offset in ems, scale and vertical squash.
    val lit = floatArrayOf(1f, 0f, 0f, 0f)
    val dy = FloatArray(count)
    val sx = FloatArray(count) { 1f }
    val sy = FloatArray(count) { 1f }
    when (style) {
        Style.Sweep, Style.Scan, Style.Dots -> Unit
        Style.Fade -> lit[Current] = 0.55f
        Style.Lift -> { lit[Current] = 0.5f; dy[1] = 0.05f; dy[2] = 0.14f; dy[3] = 0.20f }
        Style.Hop -> { lit[Current] = 0.6f; dy[Current] = -0.20f }
        Style.Swell -> { lit[Current] = 0.6f; sx[Current] = 1.3f; sy[Current] = 1.3f }
        Style.Halo -> lit[Current] = 1f
        Style.Spot -> { lit[0] = 0.85f; lit[1] = 1f; lit[2] = 0.55f; lit[3] = 0.2f }
        Style.Wave -> { lit[0] = 0.7f; lit[1] = 1f; lit[2] = 0.7f; lit[3] = 0.2f
            dy[0] = -0.14f; dy[1] = -0.20f; dy[2] = -0.14f; dy[3] = -0.05f }
        Style.Lens -> { lit[0] = 0.7f; lit[1] = 1f; lit[2] = 0.7f; lit[3] = 0.2f
            sx[0] = 1.16f; sy[0] = 1.16f; sx[1] = 1.22f; sy[1] = 1.22f; sx[2] = 1.16f; sy[2] = 1.16f; sx[3] = 1.06f; sy[3] = 1.06f }
        Style.Flip -> { lit[Current] = 0.6f; sy[Current] = 0.55f }
        Style.Spark, Style.Underline -> lit[Current] = 0.65f
    }
    val currentCenter = Offset(lefts[Current] + widths[Current] / 2f, baseline - em * 0.36f)
    if (style == Style.Spot) {
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.38f), Color.Transparent), currentCenter, size.height * 0.95f),
            size.height * 0.95f, currentCenter)
    }
    if (style == Style.Hop) {
        drawOval(Color.White.copy(alpha = 0.10f), Offset(currentCenter.x - widths[Current] * 0.45f, baseline + em * 0.10f),
            Size(widths[Current] * 0.9f, em * 0.10f))
    }

    val canvas = drawContext.canvas.nativeCanvas
    for (i in 0 until count) {
        val cx = lefts[i] + widths[i] / 2f
        val cy = baseline - em * 0.36f
        canvas.save()
        canvas.translate(cx, cy + dy[i] * em)
        canvas.scale(sx[i], sy[i])
        canvas.translate(-cx, -cy)
        paint.color = lerp(dim, accent, lit[i]).toArgb()
        if (style == Style.Halo && i == Current) paint.setShadowLayer(em * 0.55f, 0f, 0f, accent.copy(alpha = 0.95f).toArgb())
        canvas.drawText(SampleText, i, i + 1, lefts[i], baseline, paint)
        paint.clearShadowLayer()
        if (i == Current) drawCurrentOverlay(style, accent, paint, lefts[i], widths[i], baseline, em)
        canvas.restore()
    }

    if (style == Style.Underline) {
        val thickness = maxOf(2.dp.toPx(), em * 0.05f)
        val y = baseline + em * 0.14f
        drawRoundRect(accent, Offset(lefts[0], y), Size(widths[0], thickness), CornerRadius(thickness / 2f))
        drawRoundRect(accent, Offset(lefts[Current], y), Size(widths[Current] * 0.5f, thickness), CornerRadius(thickness / 2f))
    }
    if (style == Style.Spark) {
        val star = Path().apply {
            val points = floatArrayOf(50f, 0f, 61f, 39f, 100f, 50f, 61f, 61f, 50f, 100f, 39f, 61f, 0f, 50f, 39f, 39f)
            for (k in 0 until points.size step 2) {
                val px = (points[k] - 50f) / 100f
                val py = (points[k + 1] - 50f) / 100f
                if (k == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
        translate(lefts[Current] + widths[Current], baseline - em * 0.74f) {
            rotate(24f, Offset.Zero) { scale(em * 0.5f, Offset.Zero) { drawPath(star, Color.White) } }
        }
    }
}

/** The half-lit letter: where the effect is actually caught mid-way. */
private fun DrawScope.drawCurrentOverlay(
    style: Style, accent: Color, paint: Paint, left: Float, width: Float, baseline: Float, em: Float,
) {
    val canvas = drawContext.canvas.nativeCanvas
    val top = baseline - em * 0.84f
    val bottom = baseline + em * 0.16f
    fun lit(clip: () -> Unit, alpha: Float = 1f) {
        canvas.save()
        clip()
        paint.color = accent.copy(alpha = accent.alpha * alpha).toArgb()
        canvas.drawText(SampleText, Current, Current + 1, left, baseline, paint)
        canvas.restore()
    }
    when (style) {
        Style.Sweep -> {
            lit({ canvas.clipRect(left, top, left + width * 0.5f, bottom) })
            lit({ canvas.clipRect(left + width * 0.5f, top, left + width * 0.78f, bottom) }, 0.4f)
        }
        Style.Scan -> {
            val edge = left + width * 0.5f
            lit({ canvas.clipRect(left, top, edge, bottom) })
            val line = maxOf(1.5.dp.toPx(), em * 0.04f)
            drawRect(Color.White.copy(alpha = 0.22f), Offset(edge - line * 2.5f, top), Size(line * 5f, bottom - top))
            drawRect(Color.White, Offset(edge - line / 2f, top), Size(line, bottom - top))
        }
        Style.Dots -> {
            val dots = android.graphics.Path()
            val cell = em * 0.2f
            var y = top
            while (y < bottom) {
                var px = left
                while (px < left + width) {
                    dots.addCircle(px + cell / 2f, y + cell / 2f, cell * 0.40f, android.graphics.Path.Direction.CW)
                    px += cell
                }
                y += cell
            }
            lit({ canvas.clipPath(dots) })
        }
        else -> Unit
    }
}
