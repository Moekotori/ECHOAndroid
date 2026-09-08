package app.echo.android.feature.player

import app.echo.android.feature.player.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import app.echo.android.design.echoClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.KeyboardArrowDown
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import app.echo.android.design.EchoDarkGlassBorder
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoStateContent
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.rememberEchoHapticPerformer
import app.echo.android.design.progressFraction
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.PlaybackPositionState
import kotlinx.coroutines.launch

private val MiniPlayerMotionEasing = EchoMotion.Silk
private val MiniPlayerGlassRose = EchoAccent
private val MiniPlayerSwipeOutSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 640f,
)
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
    val offsetX = remember { Animatable(0f) }
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
    val compactDock = onShowDock != null || onOpenQueue != null
    val cornerRadius by animateDpAsState(
        targetValue = if (compactDock) 28.dp else 20.dp,
        animationSpec = tween(durationMillis = miniPlayerMotionDuration(420, lightweight), easing = MiniPlayerMotionEasing),
        label = "mini-player-corner",
    )
    val surfaceElevation by animateDpAsState(
        targetValue = if (compactDock) 8.dp else 6.dp,
        animationSpec = tween(durationMillis = miniPlayerMotionDuration(420, lightweight), easing = MiniPlayerMotionEasing),
        label = "mini-player-elevation",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            dark && status.isPlaying -> MiniPlayerGlassRose.copy(alpha = 0.18f)
            dark -> Color.White.copy(alpha = 0.08f)
            status.isPlaying -> scheme.primary.copy(alpha = 0.22f)
            else -> EchoGlassBorder
        },
        animationSpec = tween(durationMillis = miniPlayerMotionDuration(320, lightweight), easing = MiniPlayerMotionEasing),
        label = "mini-player-border",
    )
    val progressAlpha by animateFloatAsState(
        targetValue = if (activeDurationMs > 0L) 1f else 0.42f,
        animationSpec = tween(durationMillis = miniPlayerMotionDuration(220, lightweight), easing = MiniPlayerMotionEasing),
        label = "mini-player-progress-alpha",
    )
    val playbackDescription = stringResource(L10nR.string.feature_player_play_or_pause_37a70f)
    val shape = RoundedCornerShape(cornerRadius)
    LaunchedEffect(status.track?.id) {
        if (lightweight) {
            trackEntrance.snapTo(1f)
            return@LaunchedEffect
        }
        trackEntrance.snapTo(0.985f)
        trackEntrance.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (compactDock) 64.dp else 50.dp)
            .shadow(
                elevation = surfaceElevation,
                shape = shape,
                ambientColor = if (dark) Color.Black.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.08f),
                spotColor = if (dark) Color.Black.copy(alpha = 0.12f) else scheme.primary.copy(alpha = 0.16f),
            )
            .clip(shape)
            .background(if (dark) scheme.surface.copy(alpha = if (compactDock) 0.62f else 0.56f) else Color.Transparent)
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
            .border(BorderStroke(1.dp, borderColor), shape)
            .padding(
                start = if (compactDock) 10.dp else 14.dp,
                top = if (compactDock) 7.dp else 5.dp,
                end = if (compactDock) 10.dp else 8.dp,
                bottom = if (compactDock) 7.dp else 5.dp,
            ),
    ) {
        if (dark) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.08f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compactDock) 7.dp else 8.dp),
        ) {
            if (onShowDock != null) {
                MiniPlayerActionButton(
                    icon = Icons.Rounded.KeyboardArrowUp,
                    description = stringResource(L10nR.string.feature_player_show_bottom_bar_bf2549),
                    onClick = onShowDock,
                    compact = true,
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                    .graphicsLayer {
                        translationX = offsetX.value
                        scaleX = trackEntrance.value
                        scaleY = trackEntrance.value
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .echoClickable(enabled = onExpand != null) { onExpand?.invoke() }
                    .then(
                        if (canSwitch) {
                            Modifier.pointerInput(status.track?.id) {
                                val velocityTracker = VelocityTracker()
                                val flingThresholdPx = 780.dp.toPx()
                                detectHorizontalDragGestures(
                                    onDragStart = { velocityTracker.resetTracking() },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                                    },
                                    onDragEnd = {
                                        val velocity = velocityTracker.calculateVelocity().x
                                        val threshold = widthPx * 0.24f
                                        val settled = offsetX.value
                                        // 松手动画继承手指速度:够远或够快都切歌,弹簧全程速度连续
                                        scope.launch {
                                            when {
                                                settled <= -threshold || (velocity <= -flingThresholdPx && settled < 0f) -> {
                                                    offsetX.animateTo(
                                                        targetValue = -widthPx,
                                                        animationSpec = MiniPlayerSwipeOutSpring,
                                                        initialVelocity = velocity,
                                                    )
                                                    haptics.tick()
                                                    onNext()
                                                    offsetX.snapTo(widthPx)
                                                    offsetX.animateTo(0f, MiniPlayerSwipeReturnSpring)
                                                }
                                                settled >= threshold || (velocity >= flingThresholdPx && settled > 0f) -> {
                                                    offsetX.animateTo(
                                                        targetValue = widthPx,
                                                        animationSpec = MiniPlayerSwipeOutSpring,
                                                        initialVelocity = velocity,
                                                    )
                                                    haptics.tick()
                                                    onPrevious()
                                                    offsetX.snapTo(-widthPx)
                                                    offsetX.animateTo(0f, MiniPlayerSwipeReturnSpring)
                                                }
                                                else -> offsetX.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = MiniPlayerSwipeReturnSpring,
                                                    initialVelocity = velocity,
                                                )
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch { offsetX.animateTo(0f, MiniPlayerSwipeReturnSpring) }
                                    },
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ArtworkTile(
                    artworkUri = status.track?.artworkUri,
                    modifier = Modifier.size(if (compactDock) 42.dp else 40.dp),
                    accent = echoAccentColor(),
                    showSignal = false,
                    cornerRadius = if (compactDock) 13.dp else 11.dp,
                    elevation = 2.dp,
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
                        fontWeight = FontWeight.Bold,
                        color = if (dark) Color.White.copy(alpha = 0.96f) else scheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        status.track?.artist ?: stringResource(L10nR.string.feature_player_ready_97b946),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (dark) Color.White.copy(alpha = 0.92f) else scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
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
                        trackColor = if (dark) Color.White.copy(alpha = 0.14f) else scheme.outlineVariant.copy(alpha = 0.90f),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .semantics { contentDescription = playbackDescription }
                    .clip(RoundedCornerShape(12.dp))
                    .echoClickable(
                        enabled = status.state != EchoPlaybackState.Idle || status.track != null,
                        onClickLabel = playbackDescription,
                        onClick = {
                            haptics.confirm()
                            onPlayPause()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                EchoStateContent(state = status.isPlaying, modifier = Modifier.size(24.dp)) { playing ->
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        if (playing) {
                            PauseBarsIcon(
                                tint = if (dark) MiniPlayerGlassRose else scheme.primary,
                                height = 20.dp,
                                barWidth = 5.dp,
                                gap = 5.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = if (dark) MiniPlayerGlassRose else scheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
            when {
                onHideDock != null -> {
                    MiniPlayerActionButton(
                        icon = Icons.Rounded.KeyboardArrowDown,
                        description = stringResource(L10nR.string.feature_player_hide_bottom_bar_e918c8),
                        onClick = onHideDock,
                        compact = false,
                    )
                }
                onOpenQueue != null -> {
                    MiniPlayerActionButton(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        description = stringResource(L10nR.string.feature_player_queue_37fa6a),
                        onClick = onOpenQueue,
                        compact = true,
                    )
                }
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
    compact: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val containerColor by animateColorAsState(
        targetValue = when {
            !compact -> Color.Transparent
            dark -> Color.White.copy(alpha = 0.10f)
            else -> scheme.primary.copy(alpha = 0.10f)
        },
        animationSpec = tween(durationMillis = 220, easing = MiniPlayerMotionEasing),
        label = "mini-player-action-container",
    )
    val tint by animateColorAsState(
        targetValue = if (compact) {
            if (dark) Color.White.copy(alpha = 0.92f) else scheme.primary
        } else {
            if (dark) Color.White.copy(alpha = 0.84f) else scheme.onSurfaceVariant.copy(alpha = 0.84f)
        },
        animationSpec = tween(durationMillis = 220, easing = MiniPlayerMotionEasing),
        label = "mini-player-action-tint",
    )
    Box(
        modifier = Modifier
            .size(if (compact) 40.dp else 34.dp)
            .clip(CircleShape)
            .background(containerColor)
            .border(
                BorderStroke(
                    1.dp,
                    if (compact) {
                        if (dark) EchoDarkGlassBorder else Color.White.copy(alpha = 0.72f)
                    } else {
                        Color.Transparent
                    },
                ),
                CircleShape,
            )
            .echoClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(if (compact) 22.dp else 26.dp),
        )
    }
}
