package app.echo.android.feature.player.afterglow

import android.graphics.Typeface
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.feature.player.estimatedLyricWords
import kotlin.math.min

internal class AfterglowPlacedGlyph(
    val source: AfterglowGlyph, val x: Float, val baseline: Float, val width: Float,
    val right: Float, val top: Float, val bottom: Float, val rtl: Boolean,
    val row: Int,
)

/** Built on Default, published once, then used only by the draw thread. No measuring in a frame. */
internal class AfterglowTextLayout(
    val line: EchoLyricLine,
    val paragraph: StaticLayout,
    val captions: StaticLayout?,
    val captionGap: Int,
    val glyphs: List<AfterglowPlacedGlyph>,
    val fits: Boolean,
    val animateGlyphs: Boolean,
    val direction: AfterglowTypography,
    val displayHeight: Int,
    val rowShifts: FloatArray,
    val rowTops: FloatArray,
    val rowBottoms: FloatArray,
    val emphasis: String?,
) {
    val height: Int get() = displayHeight + captionGap + (captions?.height ?: 0)
    val highlight = AfterglowHighlight(glyphs)
    val entranceEndMs = line.startMs + minOf((glyphs.size - 1).coerceAtLeast(0) * 14L, 220L) + 300L
    val decoration = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = paragraph.paint.typeface }

    companion object {
        fun build(
            line: EchoLyricLine, endMs: Long, width: Int, height: Int, typeface: Typeface,
            textSize: Float, captionSize: Float, spacing: Float, translation: Boolean, romanization: Boolean,
            estimateWords: Boolean = false,
            composition: Int = 0,
            lightweight: Boolean = false,
        ): AfterglowTextLayout {
            val timingLine = if (estimateWords && line.words.isEmpty())
                line.copy(words = estimatedLyricWords(line, endMs)) else line
            val sourceGlyphs = afterglowGlyphs(timingLine, endMs)
            val verticalSafe = line.text.codePoints().allMatch {
                when (Character.UnicodeScript.of(it)) {
                    Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA,
                    Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL -> true
                    else -> Character.isWhitespace(it)
                }
            }
            val direction = AfterglowTypography.choose(sourceGlyphs.size, verticalSafe, composition, lightweight)
            val paragraphWidth = (width * direction.widthFraction).toInt().coerceAtLeast(1)
            val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface }
            val captionText = listOfNotNull(
                line.romanization?.takeIf { romanization && it.isNotBlank() },
                line.translation?.takeIf { translation && it.isNotBlank() },
            ).joinToString("\n")
            val captionPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                this.typeface = Typeface.DEFAULT
                this.textSize = captionSize
            }
            val alignment = if (direction == AfterglowTypography.Staggered || direction == AfterglowTypography.Margin)
                Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_CENTER
            val captions = captionText.takeIf { it.isNotEmpty() }?.let { paragraph(it, captionPaint, paragraphWidth, 1.12f, alignment) }
            var size = min(textSize * direction.sizeScale, width * 0.20f)
            if (direction == AfterglowTypography.Hero) paint.typeface = Typeface.create(typeface, Typeface.BOLD)
            paint.textSize = size
            var paragraph = paragraph(line.text, paint, paragraphWidth, spacing, alignment)
            val verticalRows = (sourceGlyphs.size + 1) / 2
            fun displayHeight(): Int = if (direction == AfterglowTypography.Vertical)
                (size * (verticalRows * 1.12f + 0.2f)).toInt() else paragraph.height
            repeat(8) {
                val gap = if (captions != null) (size * 0.32f).toInt() else 0
                if (displayHeight() + gap + (captions?.height ?: 0) > height || paragraph.lineCount > 6) {
                    size *= 0.86f
                    paint.textSize = size
                    paragraph = paragraph(line.text, paint, paragraphWidth, spacing, alignment)
                }
            }
            // Complex scripts / long text keep Android's complete paragraph shaping and draw once.
            val animatedGlyphs = sourceGlyphs.size <= 96 && line.text.codePoints().allMatch {
                when (Character.UnicodeScript.of(it)) {
                    Character.UnicodeScript.LATIN, Character.UnicodeScript.HAN,
                    Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA,
                    Character.UnicodeScript.HANGUL, Character.UnicodeScript.COMMON -> true
                    else -> false
                }
            }
            val selection = Path()
            val bounds = RectF()
            val glyphs = if (sourceGlyphs.size > 96) emptyList() else sourceGlyphs.mapIndexed { index, glyph ->
                if (direction == AfterglowTypography.Vertical) {
                    val column = index / verticalRows.coerceAtLeast(1)
                    val row = index % verticalRows.coerceAtLeast(1)
                    val measured = paint.measureText(line.text, glyph.first, glyph.end)
                    val x = paragraphWidth * 0.5f + (if (column == 0) 0.65f else -0.65f) * size - measured * 0.5f
                    val baseline = size * (0.90f + row * 1.12f)
                    return@mapIndexed AfterglowPlacedGlyph(glyph, x, baseline, measured, x + measured,
                        baseline - size, baseline + size * 0.2f, false, -1)
                }
                val row = paragraph.getLineForOffset(glyph.first)
                selection.rewind()
                paragraph.getSelectionPath(glyph.first, glyph.end, selection)
                selection.computeBounds(bounds, true)
                AfterglowPlacedGlyph(glyph, bounds.left, paragraph.getLineBaseline(row).toFloat(),
                    bounds.width(), bounds.right, bounds.top, bounds.bottom,
                    paragraph.isRtlCharAt(glyph.first), row)
            }
            val gap = if (captions != null) (size * 0.32f).toInt() else 0
            val shifts = FloatArray(paragraph.lineCount) { row ->
                if (direction == AfterglowTypography.Staggered) (if (row % 2 == 0) -1 else 1) * width * 0.035f else 0f
            }
            return AfterglowTextLayout(line, paragraph, captions, gap, glyphs,
                displayHeight() + gap + (captions?.height ?: 0) <= height && paragraph.lineCount <= 6, animatedGlyphs,
                direction, displayHeight(), shifts,
                FloatArray(paragraph.lineCount) { paragraph.getLineTop(it).toFloat() },
                FloatArray(paragraph.lineCount) { paragraph.getLineBottom(it).toFloat() },
                if (direction == AfterglowTypography.Focus) AfterglowTypography.emphasis(line.text) else null)
        }

        private fun paragraph(text: String, paint: TextPaint, width: Int, spacing: Float, alignment: Layout.Alignment): StaticLayout {
            val metrics = paint.fontMetrics
            val fontHeight = (metrics.descent - metrics.ascent + metrics.leading).coerceAtLeast(1f)
            val extra = paint.textSize * 0.24f
            // Tight spacing may remove leading, but must not overlap the shaped glyph rows.
            val multiplier = spacing.coerceIn(0.50f, 1.38f).coerceAtLeast(1f - extra / fontHeight)
            return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
                .setAlignment(alignment).setIncludePad(false)
                .setLineSpacing(extra, multiplier)
                .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED).build()
        }
    }
}
