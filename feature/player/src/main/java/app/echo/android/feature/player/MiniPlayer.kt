package app.echo.android.feature.player

import app.echo.android.feature.player.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoAccent
import app.echo.android.design.echoAccentColor
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoDarkTheme
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
private val MiniPlayerGlassRose = EchoAccent
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
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberEchoHapticPerformer()
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
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
    val cornerRadius by animateDpAsState(
        targetValue = 22.dp,
        animationSpec = tween(durationMillis = miniPlayerMotionDuration(420, lightweight), easing = MiniPlayerMotionEasing),
        label = "mini-player-corner",
    )
    val surfaceElevation by animateDpAsState(
        targetValue = if (compactDock) 3.dp else 2.dp,
        animationSpec = tween(durationMillis = miniPlayerMotionDuration(420, lightweight), easing = MiniPlayerMotionEasing),
        label = "mini-player-elevation",
    )
    val progressAlpha by animateFloatAsState(
        targetValue = if (activeDurationMs > 0L) 1f else 0.42f,
        animationSpec = tween(durationMillis = miniPlayerMotionDuration(220, lightweight), easing = MiniPlayerMotionEasing),
        label = "mini-player-progress-alpha",
    )
    val playbackDescription = stringResource(L10nR.string.feature_player_play_or_pause_37a70f)
    val shape = RoundedCornerShape(cornerRadius)
    LaunchedEffect(status.track?.id, lightweight) {
        if (lightweight) {
            trackEntrance.snapTo(1f)
            return@LaunchedEffect
        }
        trackEntrance.snapTo(0f)
        trackEntrance.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 220, easing = MiniPlayerMotionEasing),
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .shadow(
                elevation = surfaceElevation,
                shape = shape,
                ambientColor = if (dark) Color.Black.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.08f),
                spotColor = if (dark) Color.Black.copy(alpha = 0.12f) else scheme.primary.copy(alpha = 0.16f),
            )
            .clip(shape)
            .background(scheme.surface.copy(alpha = 0.98f))
            .background(
                if (dark) {
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = if (compactDock) 0.04f else 0.03f),
                            scheme.surfaceVariant.copy(alpha = if (compactDock) 0.42f else 0.34f),
                            scheme.surface.copy(alpha = if (compactDock) 0.66f else 0.58f),
                        ),
                    )
                } else {
                    Brush.verticalGradient(
                        if (compactDock) {
                            listOf(
                                Color.White,
                                Color(0xFFFBF9FA),
                                Color(0xFFF5F2F3),
                            )
                        } else {
                            listOf(
                                Color.White,
                                Color(0xFFFAFAFA),
                                Color(0xFFF4F4F5),
                            )
                        }
                    )
                },
            )
            .padding(
                start = if (compactDock) 4.dp else 12.dp,
                top = if (compactDock) 7.dp else 5.dp,
                end = 4.dp,
                bottom = if (compactDock) 7.dp else 5.dp,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onShowDock != null || onHideDock != null) {
                MiniPlayerActionButton(
                    icon = Icons.Rounded.KeyboardArrowUp,
                    description = stringResource(if (onShowDock != null) L10nR.string.feature_player_show_bottom_bar_bf2549 else L10nR.string.feature_player_hide_bottom_bar_e918c8),
                    onClick = { (onShowDock ?: onHideDock)?.invoke() },
                    rotation = if (onShowDock != null) 0f else 180f,
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                    .clipToBounds()
                    .graphicsLayer {
                        translationX = dragOffset.floatValue
                        alpha = (0.65f + 0.35f * trackEntrance.value) *
                            (1f - 0.35f * (abs(dragOffset.floatValue) / widthPx).coerceIn(0f, 1f))
                        translationY = (1f - trackEntrance.value) * 4.dp.toPx()
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
                            val threshold = widthPx * 0.22f
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ArtworkTile(
                    artworkUri = status.track?.artworkUri,
                    modifier = Modifier.size(44.dp),
                    accent = echoAccentColor(),
                    showSignal = false,
                    cornerRadius = 10.dp,
                    elevation = 0.dp,
                    placeholderIconSize = 22.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (compactDock) 2.dp else 1.dp),
                ) {
                    Text(
                        status.track?.title ?: "ECHO Mobile",
                        modifier = Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            initialDelayMillis = 700,
                            repeatDelayMillis = 1600,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        fontWeight = FontWeight.SemiBold,
                        color = if (dark) Color.White.copy(alpha = 0.96f) else scheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        status.track?.artist ?: stringResource(L10nR.string.feature_player_ready_97b946),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (dark) Color.White.copy(alpha = 0.60f) else scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Normal,
                    )
                    LinearProgressIndicator(
                        // 在绘制期读进度 State:tick 只重绘进度条,不触发任何重组
                        progress = {
                            val positionMs = positionState?.value?.positionMs
                                ?: statusState.value.positionMs
                            progressFraction(positionMs, activeDurationMs)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (compactDock) 2.dp else 1.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .graphicsLayer { alpha = progressAlpha },
                        color = if (dark) MiniPlayerGlassRose.copy(alpha = 0.62f) else scheme.primary,
                        trackColor = if (dark) Color.White.copy(alpha = 0.08f) else scheme.outlineVariant.copy(alpha = 0.55f),
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = playbackDescription }
                    .clip(RoundedCornerShape(12.dp))
                    .miniPlayerPress(
                        enabled = status.state != EchoPlaybackState.Idle || status.track != null,
                        onClick = {
                            haptics.confirm()
                            onPlayPause()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(targetState = status.isPlaying, animationSpec = tween(miniPlayerMotionDuration(140, lightweight)), modifier = Modifier.size(28.dp), label = "mini-play-pause") { playing ->
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        if (playing) {
                            PauseBarsIcon(
                                tint = if (dark) MiniPlayerGlassRose else scheme.primary,
                                height = 18.dp,
                                barWidth = 4.dp,
                                gap = 4.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = if (dark) MiniPlayerGlassRose else scheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
            }
            if (onOpenQueue != null) {
                MiniPlayerActionButton(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    description = stringResource(L10nR.string.feature_player_queue_37fa6a),
                    onClick = onOpenQueue,
                )
            }
        }
    }
}

private fun miniPlayerMotionDuration(defaultMs: Int, lightweight: Boolean): Int =
    if (lightweight) (defaultMs * 0.48f).toInt().coerceIn(90, defaultMs) else defaultMs

@Composable
private fun PauseBarsIcon(
    tint: Color,
    height: Dp,
    barWidth: Dp,
    gap: Dp,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(height)
                    .clip(RoundedCornerShape(99.dp))
                    .background(tint),
            )
        }
    }
}

@Composable
private fun MiniPlayerActionButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    rotation: Float = 0f,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val iconRotation = animateFloatAsState(
        targetValue = rotation,
        animationSpec = tween(miniPlayerMotionDuration(220, LocalEchoEffectivePerformanceMode.current.isLightweight), easing = MiniPlayerMotionEasing),
        label = "mini-dock-chevron",
    )
    val tint by animateColorAsState(
        targetValue = if (dark) Color.White.copy(alpha = 0.76f) else scheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 180, easing = MiniPlayerMotionEasing),
        label = "mini-player-action-tint",
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .miniPlayerPress(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = iconRotation.value },
        )
    }
}

/** Animate only the visual layer; the 48 dp hit target and button semantics stay intact. */
@Composable
private fun Modifier.miniPlayerPress(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val scale = animateFloatAsState(
        targetValue = if (pressed && !lightweight) 0.88f else 1f,
        animationSpec = tween(if (pressed) 80 else 180, easing = MiniPlayerMotionEasing),
        label = "mini-control-press",
    )
    return clickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        role = Role.Button,
        onClick = onClick,
    ).graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        alpha = if (!enabled) 0.38f else if (pressed) 0.70f else 1f
    }
}
