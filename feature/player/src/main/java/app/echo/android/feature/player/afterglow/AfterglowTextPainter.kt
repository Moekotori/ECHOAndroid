package app.echo.android.feature.player.afterglow

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.sin

/** All text, baseline, timing and width data are prepared before the frame; paint objects are reused. */
internal fun drawAfterglowText(
    canvas: Canvas, layout: AfterglowTextLayout, palette: AfterglowPalette,
    positionMs: Long, endMs: Long, composition: Int, motion: Boolean, wordHighlight: Boolean,
    intensity: Float, width: Float, height: Float,
) {
    val line = layout.line
    val elapsed = positionMs - line.startMs
    val alive = positionMs < endMs
    val entrance = if (motion) afterglowFraction(positionMs, line.startMs, line.startMs + 520L) else 1f
    // During gaps preserve the last line as quiet context rather than inventing singing progress.
    val exitProgress = if (motion && alive && endMs - line.startMs > 1000L)
        afterglowFraction(positionMs, endMs - 280L, endMs) else 0f
    val exit = if (alive) 1f - exitProgress * 0.80f else 0.28f
    canvas.save()
    val centerY = height * layout.direction.anchorY
    val marginX = if (layout.direction == AfterglowTypography.Margin) -width * 0.035f else 0f
    canvas.translate((width - layout.paragraph.width) * 0.5f + marginX,
        centerY - layout.height * 0.5f - exitProgress * layout.paragraph.paint.textSize * 0.22f)
    // Camera motion is small and deterministic, and does not alter measured line wrapping.
    if (motion) {
        val angle = layout.direction.tilt + sin(elapsed * 0.00022f) * 0.45f
        canvas.rotate(angle, layout.paragraph.width * 0.5f, layout.height * 0.5f)
        val push = if (layout.direction == AfterglowTypography.Hero) (1f - entrance) * 0.12f else (1f - entrance) * 0.025f
        val scale = 1f + push + sin(elapsed * 0.00018f) * 0.005f - exitProgress * 0.035f
        canvas.scale(scale, scale, layout.paragraph.width * 0.5f, layout.displayHeight * 0.5f)
    }
    val paint = layout.paragraph.paint
    val decor = layout.decoration
    layout.emphasis?.let { emphasis ->
        decor.color = palette.accent; decor.alpha = (38 * entrance * exit).toInt()
        decor.textSize = paint.textSize * 3.8f; decor.style = Paint.Style.STROKE; decor.strokeWidth = paint.textSize * 0.016f
        canvas.drawText(emphasis, layout.paragraph.width * 0.06f, layout.displayHeight * 0.45f, decor)
    }
    decor.style = Paint.Style.FILL
    decor.color = palette.accent; decor.alpha = (165 * entrance * exit).toInt()
    if (layout.direction == AfterglowTypography.Margin) {
        canvas.drawRect(-paint.textSize * 0.35f, 0f, -paint.textSize * 0.31f, layout.displayHeight * 0.72f, decor)
    } else if (layout.direction == AfterglowTypography.Subtitle) {
        val lineY = layout.displayHeight + paint.textSize * 0.13f
        canvas.drawRect(layout.paragraph.width * 0.35f, lineY, layout.paragraph.width * 0.65f, lineY + paint.textSize * 0.018f, decor)
    }
    paint.color = palette.foreground
    val glyphEntrance = motion && layout.animateGlyphs && positionMs < layout.entranceEndMs
    if (!glyphEntrance && layout.direction != AfterglowTypography.Vertical) {
        paint.alpha = (255 * entrance * exit).toInt().coerceIn(0, 255)
        drawAfterglowParagraph(canvas, layout)
        if (wordHighlight && layout.glyphs.isNotEmpty()) {
            layout.highlight.prepare(positionMs)
            paint.color = palette.highlightInk
            paint.alpha = (255 * entrance * exit * (0.65f + intensity.coerceIn(0.45f, 1.35f) * 0.25f)).toInt().coerceIn(0, 255)
            layout.highlight.draw(canvas, layout)
        }
    } else {
        for (index in layout.glyphs.indices) {
            val placed = layout.glyphs[index]
            val glyph = placed.source
            val delayMs = minOf(index * 14L, 220L)
            val enter = if (motion) afterglowFraction(positionMs, line.startMs + delayMs, line.startMs + delayMs + 300L) else 1f
            if (enter <= 0f) continue
            val eased = 1f - (1f - enter) * (1f - enter) * (1f - enter)
            val dy = (1f - eased) * paint.textSize * 0.38f
            val dx = if (placed.row >= 0) layout.rowShifts[placed.row] else 0f
            paint.color = palette.foreground
            paint.alpha = (255 * enter * exit).toInt().coerceIn(0, 255)
            canvas.drawText(line.text, glyph.first, glyph.end, placed.x + dx, placed.baseline + dy, paint)
            if (wordHighlight && glyph.startMs != null && glyph.endMs != null) {
                val fraction = afterglowFraction(positionMs, glyph.startMs, glyph.endMs)
                if (fraction > 0f) {
                    canvas.save()
                    canvas.clipRect(placed.x + dx, placed.baseline - paint.textSize * 1.4f,
                        placed.x + dx + placed.width * fraction, placed.baseline + paint.textSize * 0.5f)
                    paint.color = palette.highlightInk
                    paint.alpha = (255 * enter * exit * (0.65f + intensity.coerceIn(0.45f, 1.35f) * 0.25f)).toInt().coerceIn(0, 255)
                    canvas.drawText(line.text, glyph.first, glyph.end, placed.x + dx, placed.baseline + dy, paint)
                    canvas.restore()
                }
            }
        }
    }
    paint.alpha = 255
    layout.captions?.let { captions ->
        canvas.translate(0f, (layout.displayHeight + layout.captionGap).toFloat())
        captions.paint.color = palette.foreground
        captions.paint.alpha = (185 * entrance * exit).toInt().coerceIn(0, 255)
        captions.draw(canvas)
        captions.paint.alpha = 255
    }
    canvas.restore()
}
