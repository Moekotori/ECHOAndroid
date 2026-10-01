package app.echo.android.feature.player

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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
    lyricsEstimatedWordHighlightEnabled: Boolean,
    lyricsWordHighlightIntensity: Float,
    lyricsImmersiveModeEnabled: Boolean,
    lyricsMotionMode: String,
    showTranslation: Boolean,
    showRomanization: Boolean,
    focusGlowEnabled: Boolean,
    animationsVisible: Boolean,
    modifier: Modifier = Modifier,
    highlightColor: Color = lyricAccent,
    paper: Boolean = false,
    durationMs: Long = 0L,
) {
    BoxWithConstraints(modifier = modifier) {
        val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
        val animateFocus = animationsVisible && !lightweight
        val synced = remember(lyrics) { lyrics.isSynced }
        // 进度经 State 引用传入 item,让行 lambda 捕获保持稳定:
        // 进度 tick 只重组"当前行"(逐词高亮),行切换才重组可见行。
        val timeline = remember(lyrics) { LyricsTimeline(lyrics.lines) }
        val activeIndices by remember(timeline, positionMsState) { derivedStateOf {
            if (synced) timeline.activeAt(positionMsState.value) else emptySet()
        } }
        val activeIndex = activeIndices.minOrNull() ?: -1
        val contextIndex by remember(timeline, positionMsState) { derivedStateOf {
            if (synced) timeline.contextAt(positionMsState.value) else -1
        } }
        val focusIndex = if (activeIndex >= 0) activeIndex else contextIndex
        val listState = remember(lyrics) { androidx.compose.foundation.lazy.LazyListState() }
        val followMotion = remember(lyrics) { LyricsFollowMotion() }
        val dragging by listState.interactionSource.collectIsDraggedAsState()
        var following by remember(lyrics) { mutableStateOf(true) }
        var pendingFollowIndex by remember(lyrics) { mutableStateOf<Int?>(null) }
        var calibrationIndex by remember(lyrics) { mutableStateOf<Int?>(null) }
        LaunchedEffect(dragging) {
            if (dragging) { pendingFollowIndex = null; following = false; followMotion.reset() }
        }
        LaunchedEffect(focusIndex, pendingFollowIndex) {
            // Follow the actual seek result, not the old playback position while the command is pending.
            if (pendingFollowIndex != null && focusIndex == pendingFollowIndex) {
                pendingFollowIndex = null
                following = true
            }
        }
        LaunchedEffect(focusIndex, lyrics, following, animationsVisible, lightweight, lyricsMotionMode,
            lyricsFontFamily, lyricsFontScale, lyricsLineSpacing, showTranslation, showRomanization,
            maxWidth, maxHeight) {
            if (synced && focusIndex >= 0 && following && animationsVisible) {
                // Let typography/translation/viewport changes finish layout before reading the anchor.
                withFrameNanos { }
                followLyricsLine(listState, focusIndex, followMotion, lightweight, lyricsMotionMode)
            } else {
                followMotion.reset()
            }
        }
        val scale = lyricsFontScale.coerceIn(0.50f, 1.28f)
        val spacing = lyricsLineSpacing.coerceIn(0.50f, 1.38f)
        val motionIntensity = if (animateFocus) lyricsMotionIntensity(lyricsMotionMode) else 0f
        val immersive = lyricsImmersiveModeEnabled && synced
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().lyricsViewportFade(!lightweight),
            contentPadding = PaddingValues(
                top = if (synced) maxHeight * 0.36f else 16.dp,
                bottom = if (synced) maxHeight * 0.64f else 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy((25f * spacing).dp),
        ) {
            itemsIndexed(
                items = lyrics.lines,
                key = { index, line -> "${line.startMs}-$index-${line.text}" },
            ) { index, line ->
                val active = synced && index in activeIndices
                // Overlapping vocals may share word highlighting, but only one line grows.
                val focused = index == focusIndex
                val focusDistance = if (focused || active) 0 else if (focusIndex >= 0) abs(index - focusIndex).coerceAtMost(4) else 1
                val seekable = synced && line.startMs >= 0L
                LyricsLineItem(
                    line = line, lineEndMs = timeline.endAt(index) ?: durationMs.takeIf { it > line.startMs }, positionMsState = positionMsState,
                    active = active, focused = focused, focusDistance = focusDistance, immersive = immersive,
                    lyricsFontFamily = lyricsFontFamily, scale = scale, spacing = spacing,
                    lyricsAlignment = lyricsAlignment, lyricAccent = lyricAccent, highlightColor = highlightColor,
                    lyricsWordHighlightIntensity = lyricsWordHighlightIntensity,
                    wordHighlightEnabled = lyricsWordHighlightEnabled && !lightweight && animationsVisible,
                    estimatedWordHighlightEnabled = lyricsEstimatedWordHighlightEnabled,
                    focusGlowEnabled = focusGlowEnabled, showTranslation = showTranslation,
                    showRomanization = showRomanization, motionIntensity = motionIntensity,
                    animateFocus = animateFocus, paper = paper,
                    onClick = if (seekable) ({
                        calibrationIndex = null
                        pendingFollowIndex = index
                        onSeek(line.startMs)
                    }) else null,
                    onLongClick = { pendingFollowIndex = null; following = false; calibrationIndex = index },
                )
            }
        }
        Column(Modifier.align(Alignment.TopCenter).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!following && synced) {
                TextButton(
                    onClick = { pendingFollowIndex = null; following = true; calibrationIndex = null },
                    colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Text(stringResource(L10nR.string.lyrics_back_to_current), color = lyricAccent)
                }
            }
            calibrationIndex?.let { index ->
                TextButton(onClick = {
                    onAdjustOffset(positionMsState.value - lyrics.lines[index].startMs)
                    pendingFollowIndex = null; calibrationIndex = null; following = true
                }) { Text(stringResource(L10nR.string.lyrics_line_starts_now), color = lyricAccent) }
            }
        }
    }
}
