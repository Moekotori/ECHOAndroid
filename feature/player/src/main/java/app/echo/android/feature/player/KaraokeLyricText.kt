package app.echo.android.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
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
    glowEnabled: Boolean = true,
    motionIntensity: Float = 1f,
    estimatedWordHighlightEnabled: Boolean = false,
) {
    val words by rememberEstimatedLyricWords(line, lineEndMs, enabled && estimatedWordHighlightEnabled)
    var layout by remember(line.text) { mutableStateOf<TextLayoutResult?>(null) }
    val shapes = remember(line.text, words) { lyricWordShapes(line.text, words) }
    val clip = remember(line.text, words, lineEndMs) { KaraokeHighlightClip() }
    val timedWords = words.isNotEmpty() && shapes.size == words.size
    val timed = enabled && timedWords
    val started by remember(position, line.startMs) { derivedStateOf { position.value >= line.startMs } }
    val muted = unhighlightedAlpha?.coerceIn(0f, 1f)
        ?: (0.36f + intensity.coerceIn(0.45f, 1.35f) * 0.12f).coerceIn(0.4f, 0.55f)
    val animateInk = animationsVisible && !LocalEchoEffectivePerformanceMode.current.isLightweight
    val lightEnabled = timed && animateInk && glowEnabled
    val effects = remember(line.text, words, highlightColor, lightEnabled) {
        if (lightEnabled) KaraokeWordEffects(highlightColor) else null
    }
    val trailingEffects = remember(line.text, words, highlightColor, lightEnabled) {
        if (lightEnabled) KaraokeWordEffects(highlightColor) else null
    }
    val emphasis = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = if (animateInk) spring(dampingRatio = 1f,
            stiffness = 260f - 110f * motionIntensity.coerceIn(0f, 1f), visibilityThreshold = 0.001f)
        else tween(0),
        label = "lyric-sung-ink",
    )
    Text(
        text = line.text,
        // Neutral ink does not become a fully lit accent when a timed line gains focus.
        color = color,
        style = style, fontWeight = weight, textAlign = align,
        onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            val result = layout
            val ink = emphasis.value.coerceIn(0f, 1f)
            // Blend the neutral and accent layers in drawing, with the same motion
            // as the context scale. Color animation must not re-shape text each frame.
            val baseColor = if (timed) color.copy(alpha = color.alpha *
                if (!started) muted else (1f - ink * (1f - muted))) else color
            val now = if (timed && result != null && ink > 0f) position.value else 0L
            if (timed && result != null && ink > 0f) {
                clip.prepare(result, words, lineEndMs, shapes, now)
            }
            val light = if (lightEnabled && ink > 0f && clip.currentWordIndex >= 0)
                lyricWordGlowStrength(words[clip.currentWordIndex].startMs,
                    lyricWordEndMs(words, clip.currentWordIndex, lineEndMs), now) * intensity.coerceIn(0.45f, 1.35f) * ink
            else 0f
            if (effects != null && ink > 0f && result != null && clip.currentWordIndex >= 0) {
                effects.prepare(result, clip.currentWordIndex, shapes)
                effects.updateFront(clip.currentWordFraction)
            }
            if (timed && result != null) drawText(result, color = baseColor) else drawContent()
            if (ink > 0f && result != null) {
                if (timed) {
                    clip.completedPath?.let { completed ->
                        clipPath(completed) {
                            drawText(result, color = highlightColor.copy(alpha = highlightColor.alpha * ink * 0.86f))
                        }
                    }
                    clip.currentWordPath?.let { current ->
                        clipPath(current) {
                            val alpha = ink * lyricWordHighlightAlpha(clip.currentWordFraction, intensity)
                            if (effects != null) {
                                // Keep previous glyphs solid, including on earlier wrapped rows
                                // and in mixed direction text. Only the front glyph is feathered.
                                clipPath(effects.frontPath, ClipOp.Difference) {
                                    drawText(result, color = highlightColor.copy(alpha = highlightColor.alpha * alpha))
                                }
                                clipPath(effects.frontPath) { drawText(result, brush = effects.inkBrush, alpha = alpha) }
                                effects.drawGlyphGlow(this, result, light)
                            } else {
                                drawText(result, color = highlightColor.copy(alpha = highlightColor.alpha * alpha))
                            }
                        }
                    }
                    val previous = clip.completedWordCount - 1
                    if (trailingEffects != null && previous in words.indices) {
                        val tail = lyricWordGlowStrength(words[previous].startMs,
                            lyricWordEndMs(words, previous, lineEndMs), now) * ink * intensity.coerceIn(0.45f, 1.35f)
                        if (tail > 0f) {
                            trailingEffects.prepare(result, previous, shapes)
                            trailingEffects.updateFront(1f)
                            trailingEffects.drawGlyphGlow(this, result, tail * 0.65f)
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
