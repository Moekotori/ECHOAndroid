package app.echo.android.feature.player

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.unit.sp
import app.echo.android.model.lyrics.EchoLyricLine
import kotlin.math.ceil
import kotlin.math.max

/** Only drawing reads the high-frequency position; layout and word/glyph matching are cached. */
@Composable
internal fun VerticalLyricText(
    line: EchoLyricLine,
    lineEndMs: Long?,
    active: Boolean,
    position: State<Long>,
    wordHighlight: Boolean,
    estimatedHighlight: Boolean,
    fontFamily: FontFamily?,
    fontScale: Float,
    spacing: Float,
    color: Color,
    highlight: Color,
    highlightIntensity: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val typeface = LocalFontFamilyResolver.current.resolve(fontFamily, FontWeight.Medium).value as Typeface
    val glyphs = remember(line.text) { verticalLyricGlyphs(line.text) }
    val words by rememberEstimatedLyricWords(line, lineEndMs, active && wordHighlight && estimatedHighlight)
    val shapes = remember(line.text, words) { lyricWordShapes(line.text, words) }
    val wordIndices = remember(glyphs, shapes) {
        IntArray(glyphs.size) { index -> shapes.indexOfFirst { glyphs[index].offset in it.first until it.endExclusive } }
    }
    val textPx = with(density) { (30f * fontScale.coerceIn(0.5f, 1.28f)).sp.toPx() }
    val cell = textPx * max(1.15f, spacing.coerceIn(0.5f, 1.38f))
    BoxWithConstraints(modifier) {
        val rows = max(1, (with(density) { maxHeight.toPx() } / cell).toInt())
        val columns = max(1, ceil(glyphs.size.toDouble() / rows).toInt())
        val columnWidth = textPx * 1.5f
        val width = with(density) { (columns * columnWidth).toDp() }
        Box(Modifier.width(width).fillMaxHeight().semantics { text = AnnotatedString(line.text) }.drawWithCache {
            val nativeVertical = Build.VERSION.SDK_INT >= 36
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = textPx
                this.typeface = typeface
                if (nativeVertical) flags = flags or Paint.VERTICAL_TEXT_FLAG
                else textAlign = Paint.Align.CENTER
            }
            val base = color.toArgb()
            val lit = highlight.toArgb()
            val metrics = paint.fontMetrics
            onDrawBehind {
                val canvas = drawContext.canvas.nativeCanvas
                val now = if (active && wordHighlight) position.value else 0L
                glyphs.forEachIndexed { index, glyph ->
                    val x = size.width - (index / rows + 0.5f) * columnWidth
                    val y = (index % rows) * cell
                    paint.color = base
                    fun drawGlyph() {
                        if (nativeVertical) canvas.drawText(glyph.text, x, y, paint)
                        else canvas.drawText(glyph.text, x, y + (cell - metrics.ascent - metrics.descent) / 2, paint)
                    }
                    drawGlyph()
                    val wordIndex = wordIndices[index]
                    if (active && wordHighlight && wordIndex >= 0) {
                        val shape = shapes[wordIndex]
                        val fraction = lyricWordFraction(words[wordIndex].startMs, lyricWordEndMs(words, wordIndex, lineEndMs), now)
                        val glyphIndex = shape.glyphs.indexOf(glyph.offset)
                        val part = lyricGlyphPart(fraction, shape.glyphs.size, glyphIndex)
                        if (part > 0f) {
                            canvas.save()
                            canvas.clipRect(x - columnWidth / 2, y, x + columnWidth / 2, y + cell * part)
                            paint.color = lit
                            paint.alpha = (255 * lyricWordHighlightAlpha(fraction, highlightIntensity)).toInt()
                            drawGlyph()
                            canvas.restore()
                        }
                    }
                }
            }
        })
    }
}
