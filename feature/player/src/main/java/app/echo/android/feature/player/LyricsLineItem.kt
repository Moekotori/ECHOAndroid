package app.echo.android.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import app.echo.android.design.EchoMotion
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.settings.EchoPlayerPageStyle
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
    motion: LyricsMotionProfile,
    animateFocus: Boolean,
    paper: Boolean,
    passed: Boolean = false,
    stationNumber: Int = 1,
    onClick: (() -> Unit)?,
    onLongClick: () -> Unit,
) {
    val contextMotion = if (animateFocus) spring<Float>(dampingRatio = 1f,
        stiffness = motion.focusStiffness, visibilityThreshold = 0.001f)
    else tween(0)
    val markerAlpha = animateFloatAsState(if (focused) 1f else 0f,
        if (animateFocus) EchoMotion.silkFloat(320) else snap(), label = "lyrics-focus-marker")
    val pageStyle = LocalPlayerPageStyle.current
    val terminal = pageStyle == EchoPlayerPageStyle.Terminal
    val scrapbook = pageStyle == EchoPlayerPageStyle.Scrapbook
    val metro = pageStyle == EchoPlayerPageStyle.MetroLine
    val chat = pageStyle == EchoPlayerPageStyle.ChatThread
    // Station lists and chat bubbles read left to right whatever the alignment setting says.
    val alignment = if (metro || chat) "start" else lyricsAlignment
    val lineColor = if (chat && focused) Color.White else lyricAccent
    val lineHighlight = if (chat && focused) Color.White else highlightColor
    val textAlign = lyricsTextAlign(alignment)
    val horizontalAlignment = lyricsHorizontalAlignment(alignment)
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
        // Calm keeps a fixed size; the other presets draw within the same measured bounds.
        targetValue = motion.scale(focused, focusDistance),
        animationSpec = contextMotion,
        label = "lyrics-text-scale",
    )
    // Depth-of-field: lines fall slightly out of focus with distance. Needs RenderEffect (API 31+).
    val textBlur = animateFloatAsState(
        targetValue = if (android.os.Build.VERSION.SDK_INT >= 31 && !immersive) motion.blur(focused, focusDistance) else 0f,
        animationSpec = contextMotion,
        label = "lyrics-text-blur",
    )
    val textLift = animateFloatAsState(
        targetValue = if (focused || metro) 0f else motion.liftDp,
        animationSpec = contextMotion,
        label = "lyrics-text-lift",
    )
    Column(
        modifier = Modifier
            .then(if (chat) Modifier.widthIn(max = 320.dp) else Modifier)
            .fillMaxWidth()
            .graphicsLayer {
                // Move captions together with their line, leaving the measured scroll anchor intact.
                translationY = textLift.value.dp.toPx() * spacing.coerceAtMost(1f)
            }
            .drawBehind {
                if (chat) {
                    val fill = lerp(Color.White.copy(alpha = 0.88f), ChatStyle.Accent, markerAlpha.value)
                    drawOutline(ChatStyle.Bubble.createOutline(size, layoutDirection, this), fill)
                }
                if (metro) {
                    drawMetroLyricStation(passed, focused, markerAlpha.value, spacing)
                }
                if (terminal && markerAlpha.value > 0f) {
                    // Cursor line: a tinted row and a bar on the left edge.
                    drawRect(highlightColor.copy(alpha = 0.14f * markerAlpha.value))
                    drawRect(highlightColor.copy(alpha = markerAlpha.value), Offset.Zero, Size(2.dp.toPx(), size.height))
                }
                if (scrapbook && markerAlpha.value > 0f) {
                    drawRoundRect(
                        ScrapbookStyle.Yellow.copy(alpha = 0.85f * markerAlpha.value),
                        Offset(0f, size.height * 0.34f), Size(size.width, size.height * 0.5f), CornerRadius(3.dp.toPx()),
                    )
                }
                if (paper && !scrapbook && !metro && !chat && markerAlpha.value > 0f) {
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
            .padding(
                start = if (metro) 62.dp else if (chat) 14.dp else if (paper) 10.dp else 4.dp,
                end = if (chat) 14.dp else if (paper) 10.dp else 4.dp,
                top = if (chat) 9.dp else 2.dp,
                bottom = if (chat) 9.dp else 2.dp,
            ),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy((5f * spacing).dp),
    ) {
        if (metro) MetroLyricStationLabel(stationNumber, line.startMs, focused, passed)
        if (line.speaker != null || line.isBackground) {
            Text(text = if (line.isBackground) stringResource(L10nR.string.lyrics_backing_vocals) else line.speaker.orEmpty(),
                color = lyricAccent.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
        }
        if (terminal && line.startMs >= 0L) {
            Text(
                text = lyricTimestamp(line.startMs),
                modifier = Modifier.graphicsLayer { alpha = animatedPrimaryAlpha.value * 0.7f },
                color = highlightColor, fontFamily = TerminalStyle.Mono, fontSize = 10.5.sp,
            )
        }
        KaraokeLyricText(
            line = line,
            lineEndMs = lineEndMs,
            active = active,
            enabled = wordHighlightEnabled,
            estimatedWordHighlightEnabled = estimatedWordHighlightEnabled,
            animationsVisible = animateFocus,
            glowEnabled = focusGlowEnabled && !paper,
            motionIntensity = motion.intensity,
            position = positionMsState,
            color = lineColor,
            highlightColor = lineHighlight,
            unhighlightedAlpha = if (paper) 0.72f else null,
            intensity = lyricsWordHighlightIntensity,
            modifier = Modifier.fillMaxWidth().graphicsLayer {
                alpha = animatedPrimaryAlpha.value
                scaleX = textScale.value
                scaleY = textScale.value
                val blur = textBlur.value.dp.toPx()
                renderEffect = if (blur >= 0.5f) BlurEffect(blur, blur, TileMode.Decal) else null
                transformOrigin = TransformOrigin(if (alignment == "start") 0f else 0.5f, 0.5f)
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
                color = lineColor,
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
                color = lineColor,
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
