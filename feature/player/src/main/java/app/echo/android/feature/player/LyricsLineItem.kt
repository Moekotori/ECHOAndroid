package app.echo.android.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.feature.player.R as L10nR

@Composable
internal fun LyricsLineItem(
    line: EchoLyricLine,
    lineEndMs: Long?,
    positionMsState: State<Long>,
    active: Boolean,
    focused: Boolean,
    focusDistance: Int,
    immersive: Boolean,
    lyricsFontFamily: FontFamily?,
    scale: Float,
    spacing: Float,
    lyricsAlignment: String,
    lyricAccent: Color,
    highlightColor: Color,
    lyricsWordHighlightIntensity: Float,
    wordHighlightEnabled: Boolean,
    estimatedWordHighlightEnabled: Boolean,
    focusGlowEnabled: Boolean,
    showTranslation: Boolean,
    showRomanization: Boolean,
    motionIntensity: Float,
    animateFocus: Boolean,
    paper: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: () -> Unit,
) {
    val transitionDuration = if (animateFocus) 320 else 0
    val contextMotion = if (animateFocus) spring<Float>(dampingRatio = 1f,
        stiffness = 260f - 110f * motionIntensity.coerceIn(0f, 1f), visibilityThreshold = 0.001f)
    else tween(0)
    val textAlign = lyricsTextAlign(lyricsAlignment)
    val horizontalAlignment = lyricsHorizontalAlignment(lyricsAlignment)
    val markerAlpha = animateFloatAsState(if (focused) 1f else 0f,
        tween(transitionDuration, easing = LyricsSettingsMotionEasing), label = "lyrics-focus-marker")
    val primaryAlpha = when (focusDistance) {
        0 -> 1f
        else -> if (immersive) 0.08f else when (focusDistance) {
            1 -> 0.78f
            2 -> if (paper) 0.76f else 0.58f
            3 -> if (paper) 0.74f else 0.40f
            else -> if (paper) 0.72f else 0.28f
        }
    }
    val secondaryAlpha = when (focusDistance) {
        0 -> 0.84f
        else -> if (immersive) 0f else when (focusDistance) {
            1 -> if (paper) 0.74f else 0.64f
            2 -> if (paper) 0.72f else 0.48f
            3 -> if (paper) 0.72f else 0.34f
            else -> if (paper) 0.72f else 0.24f
        }
    }
    val animatedPrimaryAlpha = animateFloatAsState(
        targetValue = primaryAlpha,
        animationSpec = contextMotion,
        label = "lyrics-line-alpha",
    )
    val animatedSecondaryAlpha = animateFloatAsState(
        targetValue = secondaryAlpha,
        animationSpec = contextMotion,
        label = "lyrics-secondary-alpha",
    )
    val textScale = animateFloatAsState(
        // Focus changes the visual type size even with motion disabled.
        targetValue = if (focused) 1f else when (focusDistance) { 1 -> 0.90f; 2 -> 0.85f; else -> 0.82f },
        animationSpec = contextMotion,
        label = "lyrics-text-scale",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (paper && markerAlpha.value > 0f) {
                    drawLine(
                        highlightColor.copy(alpha = animatedPrimaryAlpha.value * markerAlpha.value),
                        Offset(1.dp.toPx(), 6.dp.toPx()),
                        Offset(1.dp.toPx(), size.height - 6.dp.toPx()),
                        strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round,
                    )
                }
            }
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        // Keep seeking and calibration free of a full-row ripple/background.
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = if (paper) 10.dp else 4.dp, vertical = 2.dp),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy((5f * spacing).dp),
    ) {
        if (line.speaker != null || line.isBackground) {
            Text(text = if (line.isBackground) stringResource(L10nR.string.lyrics_backing_vocals) else line.speaker.orEmpty(),
                color = lyricAccent.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
        }
        KaraokeLyricText(
            line = line,
            lineEndMs = lineEndMs,
            active = active,
            enabled = wordHighlightEnabled,
            estimatedWordHighlightEnabled = estimatedWordHighlightEnabled,
            animationsVisible = animateFocus,
            glowEnabled = focusGlowEnabled && !paper,
            motionIntensity = motionIntensity,
            position = positionMsState,
            color = lyricAccent,
            highlightColor = highlightColor,
            unhighlightedAlpha = if (paper) 0.72f else null,
            intensity = lyricsWordHighlightIntensity,
            modifier = Modifier.fillMaxWidth().graphicsLayer {
                alpha = animatedPrimaryAlpha.value
                scaleX = textScale.value
                scaleY = textScale.value
                transformOrigin = TransformOrigin(if (lyricsAlignment == "start") 0f else 0.5f, 0.5f)
            },
            // Reserve the focused size and scale only the main lyric in drawing:
            // long lines keep their wraps and the follow-scroll anchor stays stable.
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = lyricsFontFamily ?: FontFamily.SansSerif,
                fontSize = ((if (paper) 28f else 30f) * scale).sp,
                lineHeight = maxOf((if (paper) 38f else 41f) * scale * spacing,
                    (if (paper) 28f else 30f) * scale * 1.1f).sp,
                letterSpacing = 0.sp,
            ),
            weight = FontWeight.Medium,
            align = textAlign,
        )
        line.translation?.takeIf { showTranslation && it.isNotBlank() }?.let { translation ->
            Text(
                text = translation,
                modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = animatedSecondaryAlpha.value },
                color = lyricAccent,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = (14f * scale).sp,
                    lineHeight = maxOf(21f * scale * spacing, 14f * scale * 1.1f).sp,
                    letterSpacing = 0.sp,
                ),
                fontWeight = FontWeight.Normal,
                textAlign = textAlign,
            )
        }
        line.romanization?.takeIf { showRomanization && it.isNotBlank() }?.let { romanization ->
            Text(
                text = romanization,
                modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = animatedSecondaryAlpha.value * 0.92f },
                color = lyricAccent,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = (12f * scale).sp,
                    lineHeight = maxOf(18f * scale * spacing, 12f * scale * 1.1f).sp,
                ),
                fontWeight = FontWeight.Normal,
                textAlign = textAlign,
            )
        }
    }
}
