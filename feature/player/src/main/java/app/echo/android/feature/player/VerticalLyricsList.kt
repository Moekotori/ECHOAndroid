package app.echo.android.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.model.lyrics.EchoLyrics

@Composable
internal fun VerticalLyricsList(
    lyrics: EchoLyrics,
    position: State<Long>,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    fontFamily: FontFamily?,
    fontScale: Float,
    spacing: Float,
    color: Color,
    highlight: Color,
    wordHighlight: Boolean,
    estimatedHighlight: Boolean,
    highlightIntensity: Float,
    showTranslation: Boolean,
    showRomanization: Boolean,
    visible: Boolean,
    motionMode: String,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    val performance = LocalEchoEffectivePerformanceMode.current
    val animateFocus = visible && !performance.isLightweight
    val motion = lyricsMotionProfile(if (animateFocus) motionMode else "calm")
    val timeline = remember(lyrics) { LyricsTimeline(lyrics.lines) }
    val synced = remember(lyrics) { lyrics.isSynced }
    val active by remember(timeline, position) { derivedStateOf { if (synced) timeline.activeAt(position.value) else emptySet() } }
    val context by remember(timeline, position) { derivedStateOf { if (synced) timeline.contextAt(position.value) else -1 } }
    val focus = active.minOrNull() ?: context
    val list = remember(lyrics) { LazyListState(firstVisibleItemIndex = focus.coerceAtLeast(0)) }
    val followMotion = remember(lyrics) { LyricsFollowMotion() }
    val dragged by list.interactionSource.collectIsDraggedAsState()
    var following by remember(lyrics) { mutableStateOf(true) }
    var pendingSeek by remember(lyrics) { mutableStateOf<Int?>(null) }
    var calibration by remember(lyrics) { mutableStateOf<Int?>(null) }
    LaunchedEffect(dragged) { if (dragged) { following = false; pendingSeek = null; followMotion.reset() } }
    LaunchedEffect(focus, pendingSeek) {
        if (pendingSeek != null && focus == pendingSeek) { pendingSeek = null; following = true }
    }
    BoxWithConstraints(modifier) {
        val width = maxWidth
        LaunchedEffect(lyrics, focus, following, visible, width, maxHeight, fontFamily, fontScale, spacing, showTranslation, showRomanization, performance.isLightweight, motionMode) {
            if (following && focus >= 0) {
                if (!visible || performance.isLightweight) {
                    if (list.firstVisibleItemIndex != focus || list.firstVisibleItemScrollOffset != 0) list.scrollToItem(focus)
                    followMotion.reset()
                } else {
                    followLyricsLine(list, focus, followMotion, false, motionMode,
                        listOf(lyrics, width, maxHeight, fontFamily, fontScale, spacing, showTranslation, showRomanization, motionMode))
                }
            } else followMotion.reset()
        }
        LazyRow(
            state = list, reverseLayout = true,
            userScrollEnabled = interactive,
            modifier = Modifier.fillMaxSize().lyricsFrameRate(visible, false, list.isScrollInProgress, performance.isLightweight),
            contentPadding = PaddingValues(horizontal = maxWidth * 0.12f, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            itemsIndexed(lyrics.lines, key = { index, line -> "${line.startMs}-$index" }) { index, line ->
                val contextMotion = if (animateFocus) spring<Float>(dampingRatio = 1f, stiffness = motion.focusStiffness)
                    else tween(0)
                val scale = animateFloatAsState(motion.scale(!synced || index == focus, kotlin.math.abs(index - focus)),
                    contextMotion, label = "vertical-lyric-scale")
                val lift = animateFloatAsState(if (!synced || index == focus) 0f else motion.liftDp,
                    contextMotion, label = "vertical-lyric-lift")
                val alpha = animateFloatAsState(
                    if (!synced || index == focus || index in active) 1f else 0.45f,
                    contextMotion, label = "vertical-lyric-alpha",
                )
                Column(Modifier.fillParentMaxHeight().then(if (interactive) Modifier.combinedClickable(
                    onClick = {
                        if (synced && line.startMs >= 0) { pendingSeek = index; calibration = null; onSeek(line.startMs) }
                    },
                    onLongClick = if (synced && line.startMs >= 0) ({ following = false; pendingSeek = null; calibration = index }) else null,
                    indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                ) else Modifier), horizontalAlignment = Alignment.CenterHorizontally) {
                    VerticalLyricText(
                        line, timeline.endAt(index) ?: durationMs.takeIf { it > line.startMs }, index in active,
                        position, wordHighlight && visible && !performance.isLightweight, estimatedHighlight,
                        fontFamily, fontScale, spacing, color,
                        highlight, highlightIntensity, Modifier.weight(1f).graphicsLayer {
                            scaleX = scale.value; scaleY = scale.value; translationY = lift.value.dp.toPx()
                            this.alpha = alpha.value
                        },
                    )
                    if (showRomanization) line.romanization?.takeIf(String::isNotBlank)?.let {
                        Text(it, Modifier.widthIn(max = 160.dp).padding(top = 8.dp), color = color.copy(alpha = 0.7f))
                    }
                    if (showTranslation) line.translation?.takeIf(String::isNotBlank)?.let {
                        Text(it, Modifier.widthIn(max = 160.dp).padding(top = 8.dp), color = color.copy(alpha = 0.7f))
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.TopCenter)) {
            if (!following && synced) TextButton(onClick = { pendingSeek = null; calibration = null; following = true }) {
                Text(stringResource(R.string.lyrics_back_to_current), color = color)
            }
            calibration?.let { index -> TextButton(onClick = {
                onAdjustOffset(position.value - lyrics.lines[index].startMs)
                calibration = null; following = true
            }) { Text(stringResource(R.string.lyrics_line_starts_now), color = color) } }
        }
    }
}
