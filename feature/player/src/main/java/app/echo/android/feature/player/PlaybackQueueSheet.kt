package app.echo.android.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoAccent
import app.echo.android.design.EchoAccentDeep
import app.echo.android.design.EchoDarkGlassBorder
import app.echo.android.design.EchoGlassInk
import app.echo.android.design.EchoGlassNight
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.echoDarkGlassBorder
import app.echo.android.design.echoDarkGlassBrush
import app.echo.android.design.formatDuration
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.playback.PlaybackQueueState
import kotlinx.coroutines.launch

private val QueueSheetMotionEasing = CubicBezierEasing(0.16f, 1f, 0.30f, 1f)
private val QueueSheetExitEasing = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)
private val QueueSheetDragSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

@Composable
fun PlaybackQueueSheet(
    visible: Boolean,
    status: EchoPlaybackStatus,
    queueState: PlaybackQueueState,
    onDismiss: () -> Unit,
    onPlayItem: (Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = EnterTransition.None,
        exit = ExitTransition.None,
        modifier = modifier,
    ) {
        val dark = LocalEchoDarkTheme.current
        val dragOffset = remember { Animatable(0f) }
        val dragScope = rememberCoroutineScope()
        val density = LocalDensity.current
        val dismissThresholdPx = remember(density) { with(density) { 92.dp.toPx() } }
        val scrimTargetAlpha = if (dark) 0.68f else 0.24f
        val scrimAlpha by transition.animateFloat(
            transitionSpec = {
                if (targetState == EnterExitState.Visible) {
                    tween(durationMillis = 220, easing = QueueSheetMotionEasing)
                } else {
                    tween(durationMillis = 150, easing = QueueSheetExitEasing)
                }
            },
            label = "queue-sheet-scrim",
        ) { state ->
            if (state == EnterExitState.Visible) scrimTargetAlpha else 0f
        }
        val sheetProgress by transition.animateFloat(
            transitionSpec = {
                if (targetState == EnterExitState.Visible) {
                    tween(durationMillis = 430, easing = QueueSheetMotionEasing)
                } else {
                    tween(durationMillis = 230, easing = QueueSheetExitEasing)
                }
            },
            label = "queue-sheet-progress",
        ) { state ->
            if (state == EnterExitState.Visible) 1f else 0f
        }
        val contentProgress by transition.animateFloat(
            transitionSpec = {
                if (targetState == EnterExitState.Visible) {
                    tween(durationMillis = 330, delayMillis = 70, easing = QueueSheetMotionEasing)
                } else {
                    tween(durationMillis = 120, easing = QueueSheetExitEasing)
                }
            },
            label = "queue-sheet-content",
        ) { state ->
            if (state == EnterExitState.Visible) 1f else 0f
        }

        LaunchedEffect(visible) {
            if (visible) dragOffset.snapTo(0f)
        }

        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                EchoGlassNight.copy(alpha = scrimAlpha * 0.58f),
                                EchoGlassInk.copy(alpha = scrimAlpha * 0.42f),
                                EchoGlassPanel.copy(alpha = scrimAlpha * 0.50f),
                            ),
                        ),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            QueueSheetSurface(
                status = status,
                queueState = queueState,
                motionProgress = contentProgress,
                onDismiss = onDismiss,
                onPlayItem = onPlayItem,
                onRemoveItem = onRemoveItem,
                onClearQueue = onClearQueue,
                onCycleRepeatMode = onCycleRepeatMode,
                onToggleShuffle = onToggleShuffle,
                onOpenLibrary = onOpenLibrary,
                onHandleDrag = { delta ->
                    dragScope.launch {
                        dragOffset.snapTo((dragOffset.value + delta).coerceAtLeast(0f))
                    }
                },
                onHandleDragEnd = {
                    if (dragOffset.value > dismissThresholdPx) {
                        onDismiss()
                    } else {
                        dragScope.launch { dragOffset.animateTo(0f, QueueSheetDragSpring) }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        val hiddenProgress = 1f - sheetProgress
                        translationY = size.height * hiddenProgress + dragOffset.value
                        alpha = sheetProgress
                        scaleX = 0.985f + 0.015f * sheetProgress
                        scaleY = 0.992f + 0.008f * sheetProgress
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    },
            )
        }
    }
}

@Composable
private fun QueueSheetSurface(
    status: EchoPlaybackStatus,
    queueState: PlaybackQueueState,
    onDismiss: () -> Unit,
    onPlayItem: (Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onOpenLibrary: () -> Unit,
    motionProgress: Float,
    onHandleDrag: (Float) -> Unit,
    onHandleDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val currentIndex = queueState.currentIndex
    val safeCurrentIndex = remember(currentIndex, queueState.items.size) {
        currentIndex.takeIf { it >= 0 && queueState.items.isNotEmpty() }
            ?.coerceIn(0, queueState.items.lastIndex)
            ?: 0
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeCurrentIndex)
    var positionedInitialItem by remember { mutableStateOf(false) }
    val contentLiftPx = with(LocalDensity.current) { 16.dp.toPx() }

    LaunchedEffect(currentIndex, queueState.items.size) {
        if (currentIndex >= 0 && queueState.items.isNotEmpty()) {
            val targetIndex = currentIndex.coerceIn(0, queueState.items.lastIndex)
            if (positionedInitialItem) {
                listState.animateScrollToItem(targetIndex)
            } else {
                listState.scrollToItem(targetIndex)
                positionedInitialItem = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .widthIn(max = 560.dp)
            .fillMaxHeight(0.74f)
            .shadow(
                elevation = 24.dp,
                shape = shape,
                ambientColor = EchoGlassNight.copy(alpha = 0.12f),
                spotColor = scheme.primary.copy(alpha = 0.18f),
            )
            .clip(shape)
            .background(
                if (dark) {
                    echoDarkGlassBrush(1.18f)
                } else {
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.98f), Color(0xFFF4F8FF).copy(alpha = 0.98f))
                    )
                },
            )
            .border(
                if (dark) BorderStroke(1.dp, Color.White.copy(alpha = 0.34f)) else BorderStroke(1.dp, Color.White.copy(alpha = 0.72f)),
                shape,
            )
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 0.86f + 0.14f * motionProgress
                    translationY = contentLiftPx * (1f - motionProgress)
                }
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 42.dp, height = 5.dp)
                    .clip(CircleShape)
                    .queueSheetHandleDrag(
                        onDrag = onHandleDrag,
                        onDragEnd = onHandleDragEnd,
                    )
                    .background(if (dark) Color.White.copy(alpha = 0.34f) else scheme.onSurfaceVariant.copy(alpha = 0.28f)),
            )
            Spacer(Modifier.height(14.dp))
            QueueSheetHeader(
                queueState = queueState,
                status = status,
                onDismiss = onDismiss,
                onClearQueue = onClearQueue,
            )
            Spacer(Modifier.height(12.dp))
            QueueModeControls(
                repeatMode = status.repeatMode,
                shuffleEnabled = status.shuffleEnabled,
                onCycleRepeatMode = onCycleRepeatMode,
                onToggleShuffle = onToggleShuffle,
            )
            Spacer(Modifier.height(12.dp))
            if (queueState.items.isEmpty()) {
                QueueEmptyState(onOpenLibrary = onOpenLibrary)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    itemsIndexed(
                        items = queueState.items,
                        key = { index, item -> "${item.id}-$index" },
                    ) { index, item ->
                        QueueTrackRow(
                            track = item,
                            index = index,
                            active = index == queueState.currentIndex,
                            onPlay = { onPlayItem(index) },
                            onRemove = { onRemoveItem(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueSheetHeader(
    queueState: PlaybackQueueState,
    status: EchoPlaybackStatus,
    onDismiss: () -> Unit,
    onClearQueue: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "播放队列",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = queueSubtitle(queueState, status),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (queueState.items.isNotEmpty()) {
            QueueIconButton(
                icon = Icons.Rounded.DeleteOutline,
                description = "清空队列",
                onClick = onClearQueue,
            )
        }
        QueueIconButton(
            icon = Icons.Rounded.Close,
            description = "关闭队列",
            onClick = onDismiss,
        )
    }
}

@Composable
private fun QueueModeControls(
    repeatMode: EchoRepeatMode,
    shuffleEnabled: Boolean,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        QueuePillButton(
            icon = if (repeatMode == EchoRepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            title = repeatModeLabel(repeatMode),
            selected = repeatMode != EchoRepeatMode.Off,
            onClick = onCycleRepeatMode,
            modifier = Modifier.weight(1f),
        )
        QueuePillButton(
            icon = Icons.Rounded.Shuffle,
            title = if (shuffleEnabled) "随机开启" else "顺序播放",
            selected = shuffleEnabled,
            onClick = onToggleShuffle,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QueueTrackRow(
    track: EchoTrackRef,
    index: Int,
    active: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (active) {
            scheme.primary.copy(alpha = if (LocalEchoDarkTheme.current) 0.24f else 0.13f)
        } else {
            if (LocalEchoDarkTheme.current) EchoGlassPanel.copy(alpha = 0.42f) else scheme.surface.copy(alpha = 0.62f)
        },
        animationSpec = tween(durationMillis = 220, easing = QueueSheetMotionEasing),
        label = "queue-row-container",
    )
    val borderColor by animateColorAsState(
        targetValue = if (active) {
            scheme.primary.copy(alpha = 0.34f)
        } else {
            if (LocalEchoDarkTheme.current) EchoDarkGlassBorder else scheme.outlineVariant.copy(alpha = 0.22f)
        },
        animationSpec = tween(durationMillis = 220, easing = QueueSheetMotionEasing),
        label = "queue-row-border",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(containerColor)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(18.dp))
            .clickable(onClick = onPlay)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = (index + 1).toString().padStart(2, '0'),
            color = if (active) scheme.primary else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
        )
        ArtworkTile(
            artworkUri = track.artworkUri,
            modifier = Modifier.size(46.dp),
            accent = EchoAccent,
            showSignal = active,
            cornerRadius = 13.dp,
            elevation = if (active) 4.dp else 1.dp,
            placeholderIconSize = 22.dp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (active) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = track.title,
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = queueTrackDetail(track),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        QueueIconButton(
            icon = if (active) Icons.Rounded.PlayArrow else Icons.Rounded.DeleteOutline,
            description = if (active) "当前播放" else "移除曲目",
            onClick = if (active) onPlay else onRemove,
            compact = true,
        )
    }
}

private fun Modifier.queueSheetHandleDrag(
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = pointerInput(onDrag, onDragEnd) {
    detectVerticalDragGestures(
        onVerticalDrag = { change, dragAmount ->
            change.consume()
            onDrag(dragAmount)
        },
        onDragCancel = onDragEnd,
        onDragEnd = onDragEnd,
    )
}

@Composable
private fun QueueEmptyState(onOpenLibrary: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp)
            .padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(EchoAccent.copy(alpha = 0.20f), EchoAccentDeep.copy(alpha = 0.18f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("队列为空", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
        Text(
            "从曲库选择歌曲后，这里会显示真实播放顺序",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(18.dp))
        QueuePillButton(
            icon = Icons.Rounded.LibraryMusic,
            title = "去曲库",
            selected = true,
            onClick = onOpenLibrary,
        )
    }
}

@Composable
private fun QueuePillButton(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            scheme.primary.copy(alpha = if (LocalEchoDarkTheme.current) 0.22f else 0.16f)
        } else {
            if (LocalEchoDarkTheme.current) EchoGlassPanel.copy(alpha = 0.44f) else scheme.surface.copy(alpha = 0.50f)
        },
        animationSpec = tween(durationMillis = 220, easing = QueueSheetMotionEasing),
        label = "queue-pill-container",
    )
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(containerColor)
            .border(
                BorderStroke(
                    1.dp,
                    if (selected) scheme.primary.copy(alpha = 0.30f) else if (LocalEchoDarkTheme.current) EchoDarkGlassBorder else scheme.outlineVariant.copy(alpha = 0.22f),
                ),
                RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.size(7.dp))
        Text(
            text = title,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QueueIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(if (compact) 34.dp else 40.dp)
            .clip(CircleShape)
            .background(if (LocalEchoDarkTheme.current) EchoGlassPanel.copy(alpha = 0.46f) else scheme.surface.copy(alpha = 0.68f))
            .border(BorderStroke(1.dp, if (LocalEchoDarkTheme.current) EchoDarkGlassBorder else scheme.outlineVariant.copy(alpha = 0.22f)), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(if (compact) 19.dp else 22.dp),
        )
    }
}

private fun queueSubtitle(queueState: PlaybackQueueState, status: EchoPlaybackStatus): String {
    if (queueState.items.isEmpty()) return status.track?.title ?: "暂无播放"
    val current = (queueState.currentIndex + 1).coerceAtLeast(0)
    return "${queueState.items.size} 首 · 当前第 $current 首"
}

private fun queueTrackDetail(track: EchoTrackRef): String {
    val album = track.album?.takeIf { it.isNotBlank() }
    val duration = track.durationMs.takeIf { it > 0L }?.let(::formatDuration)
    return listOfNotNull(track.artist.takeIf { it.isNotBlank() }, album, duration).joinToString(" · ")
        .ifBlank { "本地队列" }
}

private fun repeatModeLabel(mode: EchoRepeatMode): String = when (mode) {
    EchoRepeatMode.Off -> "不循环"
    EchoRepeatMode.All -> "列表循环"
    EchoRepeatMode.One -> "单曲循环"
}
