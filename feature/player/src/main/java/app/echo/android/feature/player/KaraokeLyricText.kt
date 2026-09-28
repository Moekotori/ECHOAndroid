package app.echo.android.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import app.echo.android.design.LocalEchoEffectivePerformanceMode

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
    lineEndMs: Long? = line.endMs,
    highlightColor: Color = color,
    unhighlightedAlpha: Float? = null,
    animationsVisible: Boolean = true,
) {
    var layout by remember(line.text) { mutableStateOf<TextLayoutResult?>(null) }
    val shapes = remember(line.text, line.words) { lyricWordShapes(line.text, line.words) }
    val clip = remember(line.text, line.words, lineEndMs) { KaraokeHighlightClip() }
    val timedWords = line.words.isNotEmpty() && shapes.size == line.words.size
    val timed = enabled && timedWords
    val started by remember(position, line.startMs) { derivedStateOf { position.value >= line.startMs } }
    val muted = unhighlightedAlpha?.coerceIn(0f, 1f)
        ?: (0.36f + intensity.coerceIn(0.45f, 1.35f) * 0.12f).coerceIn(0.4f, 0.55f)
    val animateInk = animationsVisible && !LocalEchoEffectivePerformanceMode.current.isLightweight
    val baseColor = animateColorAsState(
        targetValue = if (timed && (active || !started)) color.copy(alpha = color.alpha * muted) else color,
        animationSpec = tween(if (animateInk) 160 else 0, easing = LyricsSettingsMotionEasing),
        label = "lyric-base-ink",
    )
    val emphasis = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(if (!animateInk) 0 else if (active) 80 else 280,
            easing = LyricsSettingsMotionEasing),
        label = "lyric-sung-ink",
    )
    Text(
        text = line.text,
        // Neutral ink does not become a fully lit accent when a timed line gains focus.
        color = baseColor.value,
        style = style, fontWeight = weight, textAlign = align,
        onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            drawContent()
            val result = layout
            val ink = emphasis.value
            if (ink > 0f && result != null) {
                if (timed) {
                    clip.prepare(result, line.words, lineEndMs, shapes, position.value)
                    clip.completedPath?.let { completed ->
                        clipPath(completed) {
                            drawText(result, color = highlightColor.copy(alpha = highlightColor.alpha * ink * 0.86f))
                        }
                    }
                    clip.currentWordPath?.let { current ->
                        clipPath(current) {
                            drawText(result, color = highlightColor.copy(alpha = highlightColor.alpha * ink *
                                lyricWordHighlightAlpha(clip.currentWordFraction, intensity)))
                        }
                    }
                } else {
                    // Plain LRC remains a sentence highlight, without invented word timings.
                    drawText(result, color = highlightColor.copy(alpha = highlightColor.alpha * ink))
                }
            }
        },
    )
}
