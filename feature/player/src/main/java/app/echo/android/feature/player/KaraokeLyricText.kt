package app.echo.android.feature.player

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
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
    val shapes = remember(line.text, line.words) { lyricWordShapes(line.text, line.words) }
    val clip = remember(line.text, line.words, line.endMs) { KaraokeHighlightClip() }
    val timedWords = remember(line.text, line.words) {
        line.words.isNotEmpty() && line.words.joinToString(separator = "") { it.text } == line.text
    }
    val timed = enabled && active && timedWords
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
                val highlight = clip.prepare(result, line.words, line.endMs, shapes, position.value)
                if (highlight != null) clipPath(highlight) { drawText(result, color = color) }
            }
        },
    )
}
