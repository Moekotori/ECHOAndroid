package app.echo.android.feature.player

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import app.echo.android.model.lyrics.EchoLyricLine

/** Read the clock in drawing only: word progress does not re-layout the text on every tick. */
@Composable
internal fun KaraokeLyricText(
    line: EchoLyricLine,
    active: Boolean,
    enabled: Boolean,
    position: State<Long>,
    color: Color,
    intensity: Float,
    style: TextStyle,
    weight: FontWeight,
    align: TextAlign,
    modifier: Modifier = Modifier,
) {
    var layout by remember(line.text) { mutableStateOf<TextLayoutResult?>(null) }
    val ranges = remember(line.words) {
        var cursor = 0
        line.words.map { word -> (cursor until cursor + word.text.length).also { cursor += word.text.length } }
    }
    val timed = enabled && active && line.words.isNotEmpty() && line.words.joinToString("") { it.text } == line.text
    val muted = (0.36f + intensity.coerceIn(0.45f, 1.35f) * 0.12f).coerceIn(0.4f, 0.55f)
    Text(
        text = line.text,
        color = if (timed) color.copy(alpha = color.alpha * muted) else color,
        style = style, fontWeight = weight, textAlign = align,
        onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            drawContent()
            val result = layout
            if (timed && result != null) {
                val now = position.value
                val path = Path()
                line.words.forEachIndexed { index, word ->
                    val end = word.endMs ?: line.words.getOrNull(index + 1)?.startMs ?: line.endMs
                    val fraction = when {
                        now < word.startMs -> 0f
                        end == null || end <= word.startMs -> 1f
                        else -> ((now - word.startMs).toFloat() / (end - word.startMs)).coerceIn(0f, 1f)
                    }
                    val range = ranges[index]
                    if (fraction >= 1f && !range.isEmpty()) {
                        path.addPath(result.getPathForRange(range.first, range.last + 1))
                    } else if (fraction > 0f) {
                        val glyphs = range.filter { !line.text[it].isLowSurrogate() }
                        val amount = fraction * glyphs.size
                        glyphs.forEachIndexed { glyphIndex, offset ->
                            val part = (amount - glyphIndex).coerceIn(0f, 1f)
                            if (part > 0f) {
                                val bounds = result.getBoundingBox(offset)
                                val rtl = result.getBidiRunDirection(offset) == androidx.compose.ui.text.style.ResolvedTextDirection.Rtl
                                path.addRect(if (rtl) Rect(bounds.right - bounds.width * part, bounds.top, bounds.right, bounds.bottom)
                                    else Rect(bounds.left, bounds.top, bounds.left + bounds.width * part, bounds.bottom))
                            }
                        }
                    }
                }
                clipPath(path) { drawText(result, color = color) }
            }
        },
    )
}
