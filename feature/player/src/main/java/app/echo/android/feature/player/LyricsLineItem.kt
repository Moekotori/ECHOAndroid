package app.echo.android.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
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
    val textAlign = lyricsTextAlign(lyricsAlignment)
    val horizontalAlignment = lyricsHorizontalAlignment(lyricsAlignment)
    val markerAlpha = animateFloatAsState(if (focused) 1f else 0f,
        tween(transitionDuration, easing = LyricsSettingsMotionEasing), label = "lyrics-focus-marker")
    val primaryAlpha = when (focusDistance) {
        0 -> 1f
        else -> if (immersive) 0.08f else when (focusDistance) {
            1 -> 0.78f
            2 -> if (paper) 0.70f else 0.58f
            3 -> if (paper) 0.62f else 0.40f
            else -> if (paper) 0.54f else 0.28f
        }
    }
    val secondaryAlpha = when (focusDistance) {
        0 -> 0.84f
        else -> if (immersive) 0f else when (focusDistance) {
            1 -> 0.64f
            2 -> if (paper) 0.60f else 0.48f
            3 -> if (paper) 0.54f else 0.34f
            else -> if (paper) 0.48f else 0.24f
        }
    }
    val animatedPrimaryAlpha by animateFloatAsState(
        targetValue = primaryAlpha,
        animationSpec = tween(durationMillis = transitionDuration, easing = LyricsSettingsMotionEasing),
        label = "lyrics-line-alpha",
    )
    val animatedSecondaryAlpha by animateFloatAsState(
        targetValue = secondaryAlpha,
        animationSpec = tween(transitionDuration, easing = LyricsSettingsMotionEasing),
        label = "lyrics-secondary-alpha",
    )
    val lineScale = animateFloatAsState(
        targetValue = if (focused) 1f + 0.036f * motionIntensity else 1f,
        animationSpec = tween(durationMillis = transitionDuration, easing = LyricsSettingsMotionEasing),
        label = "lyrics-line-scale",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = lineScale.value
                scaleY = lineScale.value
                transformOrigin = TransformOrigin(if (lyricsAlignment == "start") 0f else 0.5f, 0f)
            }
            .drawBehind {
                if (paper && markerAlpha.value > 0f) {
                    // Keep the marker inside the viewport when centered text grows around its midpoint.
                    val origin = if (lyricsAlignment == "start") 0f else 0.5f
                    val markerX = size.width * origin * (1f - 1f / lineScale.value) + 1.dp.toPx()
                    drawLine(
                        highlightColor.copy(alpha = animatedPrimaryAlpha * markerAlpha.value),
                        Offset(markerX, 6.dp.toPx()),
                        Offset(markerX, size.height - 6.dp.toPx()),
                        strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round,
                    )
                }
            }
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = if (paper) 10.dp else 4.dp, vertical = 2.dp),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy((5f * spacing).dp),
    ) {
        val activeShadow = if (active && focusGlowEnabled) {
            Shadow(
                color = Color.Black.copy(alpha = 0.22f),
                offset = Offset(0f, 2f),
                blurRadius = 8f,
            )
        } else {
            Shadow(
                color = Color.Transparent,
            )
        }
        if (line.speaker != null || line.isBackground) {
            Text(text = if (line.isBackground) stringResource(L10nR.string.lyrics_backing_vocals) else line.speaker.orEmpty(),
                color = lyricAccent.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
        }
        KaraokeLyricText(
            line = line,
            lineEndMs = lineEndMs,
            active = active,
            enabled = wordHighlightEnabled,
            animationsVisible = animateFocus,
            position = positionMsState,
            color = lyricAccent.copy(alpha = animatedPrimaryAlpha),
            highlightColor = highlightColor.copy(alpha = animatedPrimaryAlpha),
            unhighlightedAlpha = if (paper) 0.72f else null,
            intensity = lyricsWordHighlightIntensity,
            modifier = Modifier.fillMaxWidth(),
            // Keep glyph metrics stable across focus changes: resizing here
            // rewraps long lines and moves the scroll target during animation.
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = lyricsFontFamily ?: FontFamily.SansSerif,
                fontSize = ((if (paper) 24f else 26f) * scale).sp,
                lineHeight = ((if (paper) 33f else 36f) * scale * spacing).sp,
                letterSpacing = 0.sp,
                shadow = activeShadow,
            ),
            weight = FontWeight.Medium,
            align = textAlign,
        )
        line.translation?.takeIf { showTranslation && it.isNotBlank() }?.let { translation ->
            Text(
                text = translation,
                modifier = Modifier.fillMaxWidth(),
                color = lyricAccent.copy(alpha = animatedSecondaryAlpha),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = (14f * scale).sp,
                    lineHeight = (21f * scale * spacing).sp,
                    letterSpacing = 0.sp,
                ),
                fontWeight = FontWeight.Normal,
                textAlign = textAlign,
            )
        }
        line.romanization?.takeIf { showRomanization && it.isNotBlank() }?.let { romanization ->
            Text(
                text = romanization,
                modifier = Modifier.fillMaxWidth(),
                color = lyricAccent.copy(alpha = animatedSecondaryAlpha * 0.92f),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = (12f * scale).sp,
                    lineHeight = (18f * scale * spacing).sp,
                ),
                fontWeight = FontWeight.Normal,
                textAlign = textAlign,
            )
        }
    }
}
