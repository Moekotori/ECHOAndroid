package app.echo.android.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoAccent
import app.echo.android.design.EchoAccentDeep
import app.echo.android.design.EchoDarkGlassBorder
import app.echo.android.design.EchoGlassInk
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.progressFraction
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.PlaybackPositionState
import kotlinx.coroutines.launch

private val MiniPlayerMotionEasing = CubicBezierEasing(0.16f, 1f, 0.30f, 1f)

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun MiniPlayer(
    status: EchoPlaybackStatus,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    positionState: PlaybackPositionState? = null,
    onHideDock: (() -> Unit)? = null,
    onShowDock: (() -> Unit)? = null,
    onOpenQueue: (() -> Unit)? = null,
    onExpand: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val offsetX = remember { Animatable(0f) }
    val trackEntrance = remember { Animatable(1f) }
    var widthPx by remember { mutableStateOf(1f) }
    val canSwitch = onNext != null && onPrevious != null && status.track != null
    val activePositionMs = positionState?.positionMs ?: status.positionMs
    val activeDurationMs = positionState?.durationMs?.takeIf { it > 0L } ?: status.durationMs
    val compactDock = onShowDock != null || onOpenQueue != null
    val cornerRadius by animateDpAsState(
        targetValue = if (compactDock) 28.dp else 20.dp,
        animationSpec = tween(durationMillis = 420, easing = MiniPlayerMotionEasing),
        label = "mini-player-corner",
    )
    val surfaceElevation by animateDpAsState(
        targetValue = if (compactDock) 18.dp else 14.dp,
        animationSpec = tween(durationMillis = 420, easing = MiniPlayerMotionEasing),
        label = "mini-player-elevation",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            dark && status.isPlaying -> scheme.primary.copy(alpha = 0.42f)
            dark -> Color.White.copy(alpha = 0.28f)
            status.isPlaying -> scheme.primary.copy(alpha = 0.22f)
            else -> Color(0xFFE9E9EC)
        },
        animationSpec = tween(durationMillis = 320, easing = MiniPlayerMotionEasing),
        label = "mini-player-border",
    )
    val playButtonScale by animateFloatAsState(
        targetValue = if (status.isPlaying) 1f else 0.96f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "mini-player-play-scale",
    )
    val progressAlpha by animateFloatAsState(
        targetValue = if (activeDurationMs > 0L) 1f else 0.42f,
        animationSpec = tween(durationMillis = 220, easing = MiniPlayerMotionEasing),
        label = "mini-player-progress-alpha",
    )
    val shape = RoundedCornerShape(cornerRadius)
    LaunchedEffect(status.track?.id) {
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
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = scheme.primary.copy(alpha = 0.16f),
            )
            .clip(shape)
            .background(if (dark) EchoGlassInk.copy(alpha = if (compactDock) 0.90f else 0.84f) else Color.Transparent)
            .background(
                if (dark) {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (compactDock) 0.12f else 0.10f),
                            EchoGlassPanel.copy(alpha = if (compactDock) 0.76f else 0.70f),
                            EchoGlassInk.copy(alpha = if (compactDock) 0.94f else 0.88f),
                        ),
                    )
                } else {
                    Brush.verticalGradient(
                        if (compactDock) {
                            listOf(
                                Color.White,
                                Color(0xFFFBFCFF),
                                Color(0xFFF3F7FE),
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compactDock) 7.dp else 8.dp),
        ) {
            if (onShowDock != null) {
                MiniPlayerActionButton(
                    icon = Icons.Rounded.KeyboardArrowUp,
                    description = "显示底栏",
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
                    .clickable(enabled = onExpand != null) { onExpand?.invoke() }
                    .then(
                        if (canSwitch) {
                            Modifier.pointerInput(status.track?.id) {
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                                    },
                                    onDragEnd = {
                                        val threshold = widthPx * 0.24f
                                        val settled = offsetX.value
                                        scope.launch {
                                            when {
                                                settled <= -threshold -> {
                                                    offsetX.animateTo(-widthPx, tween(160))
                                                    onNext()
                                                    offsetX.snapTo(widthPx)
                                                    offsetX.animateTo(0f, tween(300))
                                                }
                                                settled >= threshold -> {
                                                    offsetX.animateTo(widthPx, tween(160))
                                                    onPrevious()
                                                    offsetX.snapTo(-widthPx)
                                                    offsetX.animateTo(0f, tween(300))
                                                }
                                                else -> offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
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
                    accent = EchoAccent,
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
                        status.track?.artist ?: "就绪",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (dark) Color.White.copy(alpha = 0.92f) else scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    LinearProgressIndicator(
                        progress = { progressFraction(activePositionMs, activeDurationMs) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (compactDock) 2.dp else 1.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .graphicsLayer { alpha = progressAlpha },
                        color = scheme.primary,
                        trackColor = if (dark) Color.White.copy(alpha = 0.28f) else scheme.outlineVariant.copy(alpha = 0.90f),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = CircleShape,
                        ambientColor = EchoAccent.copy(alpha = 0.30f),
                        spotColor = EchoAccentDeep.copy(alpha = 0.34f),
                    )
                    .graphicsLayer {
                        scaleX = playButtonScale
                        scaleY = playButtonScale
                    }
                    .clip(CircleShape)
                    .background(
                        if (dark) {
                            Brush.linearGradient(listOf(EchoAccent, EchoAccentDeep))
                        } else {
                            Brush.linearGradient(listOf(Color(0xFF111318), Color(0xFF111318)))
                        },
                    )
                    .clickable(
                        enabled = status.state != EchoPlaybackState.Idle || status.track != null,
                        onClick = onPlayPause,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "播放或暂停",
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .alpha(if (status.isPlaying) 0f else 1f),
                )
                if (status.isPlaying) {
                    PauseBarsIcon(
                        tint = Color.White,
                        height = 20.dp,
                        barWidth = 5.dp,
                        gap = 5.dp,
                    )
                }
            }
            when {
                onHideDock != null -> {
                    MiniPlayerActionButton(
                        icon = Icons.Rounded.KeyboardArrowDown,
                        description = "隐藏底栏",
                        onClick = onHideDock,
                        compact = false,
                    )
                }
                onOpenQueue != null -> {
                    MiniPlayerActionButton(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        description = "播放队列",
                        onClick = onOpenQueue,
                        compact = true,
                    )
                }
            }
        }
    }
}

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
            dark -> EchoGlassPanel.copy(alpha = 0.58f)
            else -> scheme.primary.copy(alpha = 0.10f)
        },
        animationSpec = tween(durationMillis = 220, easing = MiniPlayerMotionEasing),
        label = "mini-player-action-container",
    )
    val tint by animateColorAsState(
        targetValue = if (compact) {
            if (dark) scheme.primary.copy(alpha = 0.96f) else scheme.primary
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
            .clickable(onClick = onClick),
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
