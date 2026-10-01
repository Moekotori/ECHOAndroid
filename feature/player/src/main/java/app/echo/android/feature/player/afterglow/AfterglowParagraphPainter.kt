package app.echo.android.feature.player.afterglow

import android.graphics.Canvas

/** Draw only the clipped row when it has a planned offset; Android retains complete text shaping. */
internal fun drawAfterglowParagraph(canvas: Canvas, layout: AfterglowTextLayout) {
    if (layout.direction != AfterglowTypography.Staggered) {
        layout.paragraph.draw(canvas)
        return
    }
    for (row in layout.rowShifts.indices) {
        canvas.save()
        canvas.translate(layout.rowShifts[row], 0f)
        canvas.clipRect(-layout.paragraph.paint.textSize, layout.rowTops[row],
            layout.paragraph.width + layout.paragraph.paint.textSize, layout.rowBottoms[row])
        layout.paragraph.draw(canvas)
        canvas.restore()
    }
}
