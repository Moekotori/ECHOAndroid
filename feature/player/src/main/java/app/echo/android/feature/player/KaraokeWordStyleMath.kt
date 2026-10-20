package app.echo.android.feature.player

import androidx.compose.runtime.staticCompositionLocalOf
import app.echo.android.model.settings.EchoLyricsWordHighlightStyle
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** Lines outside the lyrics page (lock screen, immersive stage) keep the original sweep. */
internal val LocalLyricsWordHighlightStyle = staticCompositionLocalOf { EchoLyricsWordHighlightStyle.Sweep }

private const val EstimatedLeadMs = 100L
private const val EstimatedEaseMs = 150f
private const val FocusFalloff = 3f
private const val FocusSteps = 25f

/** 0 → 1 → 0 across a word: the moment a word is being sung peaks in the middle. */
internal fun lyricWordBump(progress: Float): Float = sin(progress.coerceIn(0f, 1f) * PI.toFloat())

/**
 * A guessed word time must not claim a position inside the word, so each estimated word changes as one unit
 * on a short ease-out that starts slightly before its nominal start.
 */
internal fun lyricEstimatedEnvelope(startMs: Long, now: Long): Float {
    val elapsed = now - (startMs - EstimatedLeadMs)
    if (elapsed <= 0L) return 0f
    if (elapsed >= EstimatedEaseMs) return 1f
    val remaining = 1f - elapsed / EstimatedEaseMs
    return 1f - remaining * remaining * remaining
}

/** Position of the sung point on the word axis: word i spans [i, i + 1]. */
internal fun lyricWordFocusHead(completedCount: Int, partialWord: Int, partialProgress: Float): Float =
    if (partialWord >= 0) partialWord + partialProgress.coerceIn(0f, 1f) else completedCount.coerceAtLeast(0).toFloat()

/** Gaussian lit neighbourhood around the sung point, quantized so steady words repaint rarely. */
internal fun lyricWordFocusWeight(wordIndex: Int, head: Float): Float {
    val distance = wordIndex + 0.5f - head
    return (exp(-(distance * distance) / FocusFalloff) * FocusSteps).roundToInt() / FocusSteps
}

/** Motion needs the display to animate; the light preset falls back to a plain per-word fade. */
internal fun resolveLyricWordHighlightStyle(
    style: EchoLyricsWordHighlightStyle,
    animated: Boolean,
): EchoLyricsWordHighlightStyle =
    if (style == EchoLyricsWordHighlightStyle.Sweep || animated) style else EchoLyricsWordHighlightStyle.Fade
