package app.echo.android.feature.player.afterglow

import android.graphics.Canvas
import android.graphics.Path

/** Bounded masks replace per-glyph paragraph redraws once the entrance has settled. */
internal class AfterglowHighlight(private val glyphs: List<AfterglowPlacedGlyph>) {
    private val completed = Path()
    private val partial = Path()
    private var maskLow = 0L
    private var maskHigh = 0L
    private var hasCompleted = false
    private var hasPartial = false

    fun prepare(positionMs: Long) {
        var low = 0L
        var high = 0L
        partial.rewind()
        hasPartial = false
        for (i in glyphs.indices) {
            val placed = glyphs[i]
            val start = placed.source.startMs ?: continue
            val end = placed.source.endMs ?: continue
            if (positionMs >= end) {
                if (i < 64) low = low or (1L shl i) else high = high or (1L shl (i - 64))
            } else if (positionMs > start) {
                val fraction = afterglowFraction(positionMs, start, end)
                val edge = if (placed.rtl) placed.right - placed.width * fraction else placed.x + placed.width * fraction
                partial.addRect(if (placed.rtl) edge else placed.x, placed.top,
                    if (placed.rtl) placed.right else edge, placed.bottom, Path.Direction.CW)
                hasPartial = true
            }
        }
        if (low != maskLow || high != maskHigh) {
            completed.rewind()
            var inRun = false
            var left = 0f; var right = 0f; var top = 0f; var bottom = 0f
            for (i in glyphs.indices) {
                if (if (i < 64) low and (1L shl i) != 0L else high and (1L shl (i - 64)) != 0L) {
                    val glyph = glyphs[i]
                    // Merge touching cells on the same row; keep gaps / bidi runs exact.
                    if (inRun && glyph.top == top && glyph.bottom == bottom && glyph.x <= right + 0.75f && glyph.right >= left - 0.75f) {
                        left = minOf(left, glyph.x); right = maxOf(right, glyph.right)
                    } else {
                        if (inRun) completed.addRect(left, top, right, bottom, Path.Direction.CW)
                        left = glyph.x; right = glyph.right; top = glyph.top; bottom = glyph.bottom; inRun = true
                    }
                } else if (inRun) {
                    completed.addRect(left, top, right, bottom, Path.Direction.CW); inRun = false
                }
            }
            if (inRun) completed.addRect(left, top, right, bottom, Path.Direction.CW)
            maskLow = low; maskHigh = high
            hasCompleted = low != 0L || high != 0L
        }
    }

    fun draw(canvas: Canvas, layout: AfterglowTextLayout) {
        if (hasCompleted) {
            if (layout.direction == AfterglowTypography.Staggered) drawRows(canvas, layout, completed)
            else { canvas.save(); canvas.clipPath(completed); layout.paragraph.draw(canvas); canvas.restore() }
        }
        if (hasPartial) {
            if (layout.direction == AfterglowTypography.Staggered) drawRows(canvas, layout, partial)
            else { canvas.save(); canvas.clipPath(partial); layout.paragraph.draw(canvas); canvas.restore() }
        }
    }

    private fun drawRows(canvas: Canvas, layout: AfterglowTextLayout, mask: Path) {
        for (row in layout.rowShifts.indices) {
            canvas.save(); canvas.translate(layout.rowShifts[row], 0f)
            canvas.clipRect(-layout.paragraph.paint.textSize, layout.rowTops[row],
                layout.paragraph.width + layout.paragraph.paint.textSize, layout.rowBottoms[row])
            canvas.clipPath(mask); layout.paragraph.draw(canvas); canvas.restore()
        }
    }
}
