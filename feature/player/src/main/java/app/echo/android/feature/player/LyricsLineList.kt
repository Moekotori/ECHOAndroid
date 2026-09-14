package app.echo.android.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.feature.player.R as L10nR
import kotlin.math.abs

@Composable
internal fun LyricsLineList(
    lyrics: EchoLyrics,
    onAdjustOffset: (Long) -> Unit,
    positionMsState: State<Long>,
    onSeek: (Long) -> Unit,
    lyricsFontFamily: FontFamily?,
    lyricsFontScale: Float,
    lyricAccent: Color,
    lyricsAlignment: String,
    lyricsLineSpacing: Float,
    lyricsWordHighlightEnabled: Boolean,
    lyricsWordHighlightIntensity: Float,
    lyricsImmersiveModeEnabled: Boolean,
    lyricsMotionMode: String,
    showTranslation: Boolean,
    showRomanization: Boolean,
    focusGlowEnabled: Boolean,
    animationsVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
        val animateFocus = animationsVisible && !lightweight
        val transitionDuration = if (animateFocus) 320 else 0
        val scrollDuration = when {
            lightweight -> 240
            lyricsMotionMode == "calm" -> 360
            lyricsMotionMode == "stage" -> 560
            else -> 480
        }
        val synced = lyrics.isSynced
        // 进度经 State 引用传入 item,让行 lambda 捕获保持稳定:
        // 进度 tick 只重组"当前行"(逐词高亮),行切换才重组可见行。
        val timeline = remember(lyrics) { LyricsTimeline(lyrics.lines) }
        val activeIndices by remember(timeline) { derivedStateOf {
            if (synced) timeline.activeAt(positionMsState.value) else emptySet()
        } }
        val activeIndex = activeIndices.minOrNull() ?: -1
        val listState = rememberLazyListState()
        val dragging by listState.interactionSource.collectIsDraggedAsState()
        var following by remember(lyrics) { mutableStateOf(true) }
        var calibrationIndex by remember(lyrics) { mutableStateOf<Int?>(null) }
        LaunchedEffect(dragging) { if (dragging) following = false }
        LaunchedEffect(activeIndex, lyrics, following, animationsVisible, scrollDuration) {
            if (synced && activeIndex >= 0 && following && animationsVisible) {
                val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
                if (target != null) {
                    // Item offsets exclude the leading content padding: zero is
                    // the focus anchor. Move the whole context with one animation.
                    listState.animateScrollBy(
                        value = target.offset.toFloat(),
                        animationSpec = tween(scrollDuration, easing = FastOutSlowInEasing),
                    )
                } else {
                    // Seeking outside the visible context still travels to the line.
                    listState.animateScrollToItem(activeIndex)
                }
            }
        }
        val scale = lyricsFontScale.coerceIn(0.82f, 1.28f)
        val spacing = lyricsLineSpacing.coerceIn(0.82f, 1.38f)
        val textAlign = lyricsTextAlign(lyricsAlignment)
        val horizontalAlignment = lyricsHorizontalAlignment(lyricsAlignment)
        val motionIntensity = if (animateFocus) lyricsMotionIntensity(lyricsMotionMode) else 0f
        val immersive = lyricsImmersiveModeEnabled && synced
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = maxHeight * 0.54f,
                bottom = maxHeight * 0.46f,
            ),
            verticalArrangement = Arrangement.spacedBy((18f * spacing).dp),
        ) {
            itemsIndexed(
                items = lyrics.lines,
                key = { index, line -> "${line.startMs}-$index-${line.text}" },
            ) { index, line ->
                val active = synced && index in activeIndices
                val focusDistance = if (active) 0 else if (activeIndex >= 0) abs(index - activeIndex).coerceAtMost(4) else 1
                val seekable = synced && line.startMs >= 0L
                val primaryAlpha = when (focusDistance) {
                    0 -> 1f
                    else -> if (immersive) 0.08f else when (focusDistance) {
                        1 -> 0.78f
                        2 -> 0.58f
                        3 -> 0.40f
                        else -> 0.28f
                    }
                }
                val secondaryAlpha = when (focusDistance) {
                    0 -> 0.84f
                    else -> if (immersive) 0f else when (focusDistance) {
                        1 -> 0.64f
                        2 -> 0.48f
                        3 -> 0.34f
                        else -> 0.24f
                    }
                }
                val backgroundAlpha = when (focusDistance) {
                    0 -> if (immersive) 0.08f else 0f
                    else -> 0f
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
                val animatedBackgroundAlpha by animateFloatAsState(
                    targetValue = backgroundAlpha,
                    animationSpec = tween(transitionDuration, easing = LyricsSettingsMotionEasing),
                    label = "lyrics-background-alpha",
                )
                val lineScale = animateFloatAsState(
                    targetValue = if (active) 1f + 0.036f * motionIntensity else 1f,
                    animationSpec = tween(durationMillis = transitionDuration, easing = LyricsSettingsMotionEasing),
                    label = "lyrics-line-scale",
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = lineScale.value
                            scaleY = lineScale.value
                            transformOrigin = TransformOrigin(if (lyricsAlignment == "start") 0f else 0.5f, 0.5f)
                        }
                        .background(
                            lyricAccent.copy(alpha = animatedBackgroundAlpha),
                            RoundedCornerShape(18.dp),
                        )
                        .then(
                            if (seekable) {
                                Modifier.combinedClickable(onClick = { onSeek(line.startMs) }, onLongClick = { following = false; calibrationIndex = index })
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
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
                    val wordHighlightEnabled = lyricsWordHighlightEnabled &&
                        !lightweight && animationsVisible
                    if (line.speaker != null || line.isBackground) {
                        Text(text = if (line.isBackground) stringResource(L10nR.string.lyrics_backing_vocals) else line.speaker.orEmpty(),
                            color = lyricAccent.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                    }
                    KaraokeLyricText(
                        line = line,
                        active = active,
                        enabled = wordHighlightEnabled,
                        position = positionMsState,
                        color = lyricAccent.copy(alpha = animatedPrimaryAlpha),
                        intensity = lyricsWordHighlightIntensity,
                        modifier = Modifier.fillMaxWidth(),
                        // Keep glyph metrics stable across focus changes: resizing here
                        // rewraps long lines and moves the scroll target during animation.
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = lyricsFontFamily ?: FontFamily.SansSerif,
                            fontSize = (24f * scale).sp,
                            lineHeight = (33f * scale * spacing).sp,
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
                                fontFamily = lyricsFontFamily ?: FontFamily.SansSerif,
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
                                fontFamily = lyricsFontFamily,
                                fontSize = (12f * scale).sp,
                                lineHeight = (18f * scale * spacing).sp,
                            ),
                            fontWeight = FontWeight.Normal,
                            textAlign = textAlign,
                        )
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.TopCenter).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (synced && activeIndex < 0) {
                val seconds by remember(timeline) { derivedStateOf {
                    timeline.nextStart(positionMsState.value)?.let { ((it - positionMsState.value + 999) / 1000).coerceAtLeast(0) }
                } }
                seconds?.let { Text(stringResource(L10nR.string.lyrics_vocals_in, it), color = lyricAccent) }
            }
            if (!following && synced) {
                TextButton(
                    onClick = { following = true; calibrationIndex = null },
                    colors = ButtonDefaults.textButtonColors(containerColor = Color.Black),
                ) {
                    Text(stringResource(L10nR.string.lyrics_back_to_current), color = lyricAccent)
                }
            }
            calibrationIndex?.let { index ->
                TextButton(onClick = {
                    onAdjustOffset(positionMsState.value - lyrics.lines[index].startMs)
                    calibrationIndex = null; following = true
                }) { Text(stringResource(L10nR.string.lyrics_line_starts_now), color = lyricAccent) }
            }
        }
    }
}
