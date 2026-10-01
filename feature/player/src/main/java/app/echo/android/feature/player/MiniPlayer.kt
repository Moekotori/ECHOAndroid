package app.echo.android.feature.player

import app.echo.android.feature.player.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import app.echo.android.design.echoClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoPlayerArtwork
import app.echo.android.design.echoChromeColors
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.rememberEchoHapticPerformer
import app.echo.android.design.progressFraction
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.PlaybackPositionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

private val MiniPlayerMotionEasing = EchoMotion.Silk
private val MiniPlayerSwipeReturnSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun MiniPlayer(
    status: EchoPlaybackStatus,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    positionState: State<PlaybackPositionState>? = null,
    onHideDock: (() -> Unit)? = null,
    onShowDock: (() -> Unit)? = null,
    onOpenQueue: (() -> Unit)? = null,
    onExpand: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    animationsVisible: Boolean = true,
    dockExpansion: State<Float>? = null,
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberEchoHapticPerformer()
    val chrome = echoChromeColors()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val motionEnabled = animationsVisible &&
        LocalWindowInfo.current.isWindowFocused && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val marqueeActive = motionEnabled && status.isPlaying && !lightweight
    val dragOffset = remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val trackEntrance = remember { Animatable(1f) }
    var widthPx by remember { mutableStateOf(1f) }
    val canSwitch = onNext != null && onPrevious != null && status.track != null
    // 进度经 State 引用传入,tick 不重组整卡:进度条用 progress lambda 在绘制期读值
    val statusState = rememberUpdatedState(status)
    val activeDurationMs by remember(positionState) {
        derivedStateOf {
            positionState?.value?.durationMs?.takeIf { it > 0L } ?: statusState.value.durationMs
        }
    }
    val density = LocalDensity.current
    val flingThresholdPx = with(density) { 780.dp.toPx() }
    val minimumFlingDistancePx = with(density) { 12.dp.toPx() }
    val nextAction by rememberUpdatedState(onNext)
    val previousAction by rememberUpdatedState(onPrevious)
    val compactDock = onShowDock != null || onOpenQueue != null
    val progressAlpha = animateFloatAsState(
        targetValue = if (activeDurationMs > 0L) 1f else 0.42f,
        animationSpec = if (!motionEnabled) snap() else tween(durationMillis = miniPlayerMotionDuration(220, lightweight), easing = MiniPlayerMotionEasing),
        label = "mini-player-progress-alpha",
    )
    val playbackDescription = stringResource(L10nR.string.feature_player_play_or_pause_37a70f)
    LaunchedEffect(motionEnabled) {
        if (!motionEnabled) {
            settleJob?.cancel()
            dragOffset.floatValue = 0f
        }
    }
    var previousTrackId by remember { mutableStateOf(status.track?.id) }
    LaunchedEffect(status.track?.id, lightweight, motionEnabled) {
        val changed = previousTrackId != status.track?.id
        previousTrackId = status.track?.id
        if (!changed || lightweight || !motionEnabled) {
            trackEntrance.snapTo(1f)
            return@LaunchedEffect
        }
        trackEntrance.snapTo(0f)
        trackEntrance.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 220, easing = MiniPlayerMotionEasing),
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp),
    ) {
        LinearProgressIndicator(
            // Read progress during drawing; ticks do not rebuild the playback row.
            progress = {
                val positionMs = positionState?.value?.positionMs ?: statusState.value.positionMs
                progressFraction(positionMs, activeDurationMs)
            },
            modifier = Modifier.fillMaxWidth().height(1.dp)
                .graphicsLayer { alpha = progressAlpha.value },
            color = chrome.secondary,
            trackColor = chrome.separator,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.weight(1f)
                    .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .graphicsLayer {
                            translationX = dragOffset.floatValue
                            alpha = (0.82f + 0.18f * trackEntrance.value) *
                                (1f - 0.55f * (abs(dragOffset.floatValue) / (widthPx * MiniPlayerSwipeCommitFraction)).coerceIn(0f, 1f))
                            translationY = (1f - trackEntrance.value) * 2.dp.toPx()
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .echoClickable(enabled = onExpand != null) { onExpand?.invoke() }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            enabled = canSwitch,
                            state = rememberDraggableState { delta ->
                                dragOffset.floatValue = (dragOffset.floatValue + delta)
                                    .coerceIn(-widthPx * 0.45f, widthPx * 0.45f)
                            },
                            onDragStarted = { settleJob?.cancel() },
                            onDragStopped = { velocity ->
                                val offset = dragOffset.floatValue
                                val threshold = widthPx * MiniPlayerSwipeCommitFraction
                                val fastSwipe = abs(velocity) > flingThresholdPx && abs(offset) > minimumFlingDistancePx && velocity * offset > 0f
                                if (abs(offset) >= threshold || fastSwipe) {
                                    haptics.tick()
                                    if (offset < 0f) nextAction?.invoke() else previousAction?.invoke()
                                }
                                settleJob = scope.launch {
                                    if (lightweight) {
                                        dragOffset.floatValue = 0f
                                    } else {
                                        animate(
                                            initialValue = dragOffset.floatValue,
                                            targetValue = 0f,
                                            initialVelocity = velocity.coerceIn(-1800f, 1800f),
                                            animationSpec = MiniPlayerSwipeReturnSpring,
                                        ) { value, _ -> dragOffset.floatValue = value }
                                    }
                                }
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EchoPlayerArtwork(
                        artworkUri = status.track?.artworkUri,
                        trackId = status.track?.id,
                        expandedArtwork = false,
                        restingCornerRadius = 8.dp,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(if (compactDock) 2.dp else 1.dp),
                    ) {
                        Text(
                            status.track?.title ?: "ECHO Mobile",
                            modifier = Modifier.basicMarquee(
                                iterations = if (marqueeActive) Int.MAX_VALUE else 0,
                                initialDelayMillis = 700,
                                repeatDelayMillis = 1600,
                            ),
                            maxLines = 1,
                            overflow = if (marqueeActive) TextOverflow.Clip else TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium,
                            color = chrome.content,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            status.track?.artist ?: stringResource(L10nR.string.feature_player_ready_97b946),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = chrome.secondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                }
                if (canSwitch) {
                    MiniPlayerSwipeFeedback(
                        offset = { dragOffset.floatValue },
                        width = { widthPx },
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = playbackDescription }
                    .clip(RoundedCornerShape(12.dp))
                    .miniPlayerPress(
                        motionEnabled = motionEnabled,
                        enabled = status.state != EchoPlaybackState.Idle || status.track != null,
                        onClick = {
                            haptics.confirm()
                            onPlayPause()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                MiniPlayerPlayPauseIcon(status.isPlaying, motionEnabled)
            }
            if (onOpenQueue != null) {
                MiniPlayerActionButton(
                    icon = PlayerControlIcons.Queue,
                    description = stringResource(L10nR.string.feature_player_queue_37fa6a),
                    onClick = onOpenQueue,
                    motionEnabled = motionEnabled,
                )
            }
            if (onShowDock != null || onHideDock != null) {
                MiniPlayerActionButton(
                    icon = PlayerControlIcons.Collapse,
                    description = stringResource(if (onShowDock != null) L10nR.string.feature_player_show_bottom_bar_bf2549 else L10nR.string.feature_player_hide_bottom_bar_e918c8),
                    onClick = { (onShowDock ?: onHideDock)?.invoke() },
                    rotation = if (onShowDock != null) 180f else 0f,
                    motionEnabled = motionEnabled,
                    dockExpansion = dockExpansion,
                )
            }
        }
    }
}
