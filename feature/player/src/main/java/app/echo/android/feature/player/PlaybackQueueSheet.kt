package app.echo.android.feature.player

import app.echo.android.feature.player.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import app.echo.android.design.echoClickable
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoMotion
import app.echo.android.design.echoAccentColor
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.formatDuration
import app.echo.android.design.echoTheme
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.playback.PlaybackQueueState
import kotlinx.coroutines.launch

private val QueueSheetMotionEasing = EchoMotion.Silk
private val QueueSheetExitEasing = EchoMotion.SilkExit
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
    onMoveItem: (Int, Int) -> Unit = { _, _ -> },
    onClearQueue: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
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
                    tween(durationMillis = 280, easing = QueueSheetMotionEasing)
                } else {
                    tween(durationMillis = 180, easing = QueueSheetExitEasing)
                }
            },
            label = "queue-sheet-scrim",
        ) { state ->
            if (state == EnterExitState.Visible) scrimTargetAlpha else 0f
        }
        // 弹簧驱动:半路打断(快速开关)时速度连续,不会出现 tween 重启的顿挫
        val sheetProgress by transition.animateFloat(
            transitionSpec = {
                if (targetState == EnterExitState.Visible) {
                    EchoMotion.silkFloat(500)
                } else {
                    EchoMotion.silkFloat(320)
                }
            },
            label = "queue-sheet-progress",
        ) { state ->
            if (state == EnterExitState.Visible) 1f else 0f
        }
        val contentProgress by transition.animateFloat(
            transitionSpec = {
                if (targetState == EnterExitState.Visible) {
                    EchoMotion.silkFloat(430)
                } else {
                    EchoMotion.silkFloat(200)
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
                                echoTheme().night.copy(alpha = scrimAlpha * 0.58f),
                                echoTheme().ink.copy(alpha = scrimAlpha * 0.42f),
                                echoTheme().panel.copy(alpha = scrimAlpha * 0.50f),
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
                onMoveItem = onMoveItem,
                onClearQueue = onClearQueue,
                onCycleRepeatMode = onCycleRepeatMode,
                onToggleShuffle = onToggleShuffle,
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
    onMoveItem: (Int, Int) -> Unit,
    onClearQueue: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    motionProgress: Float,
    onHandleDrag: (Float) -> Unit,
    onHandleDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalEchoDarkTheme.current
    val empty = queueState.items.isEmpty()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
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
            .then(if (empty) Modifier.wrapContentHeight() else Modifier.fillMaxHeight(0.74f))
            .then(if (lightweight) Modifier else Modifier.animateContentSize(EchoMotion.silkSize(360)))
            .clip(shape)
            .background(if (dark) echoTheme().panel else Color(0xFFF4F1F3))
            .border(
                BorderStroke(1.dp, if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.72f)),
                shape,
            )
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (empty) Modifier else Modifier.fillMaxSize())
                .graphicsLayer {
                    alpha = 0.86f + 0.14f * motionProgress
                    translationY = contentLiftPx * (1f - motionProgress)
                }
                .padding(horizontal = 20.dp, vertical = 8.dp),
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
                    .background(echoTheme().muted.copy(alpha = 0.35f)),
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
                QueueEmptyState()
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
                            lastIndex = queueState.items.lastIndex,
                            active = index == queueState.currentIndex,
                            onPlay = { onPlayItem(index) },
                            onRemove = { onRemoveItem(index) },
                            onMoveUp = { onMoveItem(index, index - 1) },
                            onMoveDown = { onMoveItem(index, index + 1) },
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
    val dark = LocalEchoDarkTheme.current
    val titleColor = if (dark) Color.White else echoTheme().heading
    val mutedColor = if (dark) Color.White.copy(alpha = 0.65f) else echoTheme().muted
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = stringResource(L10nR.string.feature_player_queue_37fa6a),
                color = titleColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.15.sp,
            )
            Text(
                text = queueSubtitle(queueState, status),
                color = mutedColor,
                style = MaterialTheme.typography.bodySmall,
                letterSpacing = 0.1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (queueState.items.isNotEmpty()) {
            GlyphButton(
                icon = Icons.Rounded.DeleteOutline,
                description = stringResource(L10nR.string.feature_player_clear_queue_eae952),
                touchSize = 44.dp,
                iconSize = 22.dp,
                tint = titleColor,
                background = Color.Transparent,
                onClick = onClearQueue,
            )
        }
        GlyphButton(
            icon = Icons.Rounded.Close,
            description = stringResource(L10nR.string.feature_player_close_queue_a65caf),
            touchSize = 44.dp,
            iconSize = 22.dp,
            tint = titleColor,
            background = Color.Transparent,
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
            title = if (shuffleEnabled) {
                stringResource(L10nR.string.feature_player_shuffle_on_c7c5c4)
            } else {
                stringResource(L10nR.string.feature_player_in_order_47b60a)
            },
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
    lastIndex: Int,
    active: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (active) {
            scheme.primary.copy(alpha = if (LocalEchoDarkTheme.current) 0.30f else 0.13f)
        } else {
            if (LocalEchoDarkTheme.current) echoTheme().panel.copy(alpha = 0.64f) else scheme.surface.copy(alpha = 0.62f)
        },
        animationSpec = tween(durationMillis = 220, easing = QueueSheetMotionEasing),
        label = "queue-row-container",
    )
    val borderColor by animateColorAsState(
        targetValue = if (active) {
            scheme.primary.copy(alpha = 0.34f)
        } else {
            if (LocalEchoDarkTheme.current) echoTheme().glassBorder else scheme.outlineVariant.copy(alpha = 0.22f)
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
            .echoClickable(onClick = onPlay)
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
            accent = echoAccentColor(),
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
        if (index > 0) {
            QueueIconButton(
                icon = Icons.Rounded.KeyboardArrowUp,
                description = stringResource(L10nR.string.feature_player_move_up_e6d961),
                onClick = onMoveUp,
                compact = true,
            )
        }
        if (index < lastIndex) {
            QueueIconButton(
                icon = Icons.Rounded.KeyboardArrowDown,
                description = stringResource(L10nR.string.feature_player_move_down_cf81ae),
                onClick = onMoveDown,
                compact = true,
            )
        }
        QueueIconButton(
            icon = if (active) Icons.Rounded.PlayArrow else Icons.Rounded.DeleteOutline,
            description = if (active) {
                stringResource(L10nR.string.feature_player_now_playing_214a7c)
            } else {
                stringResource(L10nR.string.feature_player_remove_track_60c516)
            },
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
private fun QueueEmptyState() {
    val dark = LocalEchoDarkTheme.current
    val titleColor = if (dark) Color.White else echoTheme().heading
    val mutedColor = if (dark) Color.White.copy(alpha = 0.68f) else echoTheme().muted
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = echoAccentColor(),
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(L10nR.string.feature_player_queue_is_empty_1b4178),
            color = titleColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(L10nR.string.feature_player_after_you_pick_songs_from_the_library_the_1b16d7),
            color = mutedColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.2.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
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
    val dark = LocalEchoDarkTheme.current
    val accent = echoAccentColor()
    val titleColor = if (dark) Color.White else echoTheme().heading
    val mutedColor = if (dark) Color.White.copy(alpha = 0.72f) else echoTheme().muted
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            accent.copy(alpha = if (dark) 0.22f else 0.16f)
        } else {
            if (dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.72f)
        },
        animationSpec = tween(durationMillis = 220, easing = QueueSheetMotionEasing),
        label = "queue-pill-container",
    )
    Row(
        modifier = modifier
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(
                BorderStroke(
                    1.dp,
                    if (selected) {
                        accent.copy(alpha = 0.55f)
                    } else if (dark) {
                        Color.White.copy(alpha = 0.08f)
                    } else {
                        Color.Transparent
                    },
                ),
                RoundedCornerShape(14.dp),
            )
            .echoClickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (selected) accent else mutedColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = title,
            color = if (selected) titleColor else mutedColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.15.sp,
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
            .background(if (LocalEchoDarkTheme.current) echoTheme().panel.copy(alpha = 0.64f) else scheme.surface.copy(alpha = 0.68f))
            .border(BorderStroke(1.dp, if (LocalEchoDarkTheme.current) echoTheme().glassBorder else scheme.outlineVariant.copy(alpha = 0.22f)), CircleShape)
            .echoClickable(onClick = onClick),
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

@Composable
private fun queueSubtitle(queueState: PlaybackQueueState, status: EchoPlaybackStatus): String {
    if (queueState.items.isEmpty()) {
        return status.track?.title ?: stringResource(L10nR.string.feature_player_nothing_playing_3beac0)
    }
    val current = (queueState.currentIndex + 1).coerceAtLeast(0)
    val size = queueState.items.size
    return stringResource(L10nR.string.feature_player_size_tracks_now_playing_current_7d4647, (size).toString(), (current).toString())
}

@Composable
private fun queueTrackDetail(track: EchoTrackRef): String {
    val album = track.album?.takeIf { it.isNotBlank() }
    val duration = track.durationMs.takeIf { it > 0L }?.let(::formatDuration)
    return listOfNotNull(track.artist.takeIf { it.isNotBlank() }, album, duration).joinToString(" · ")
        .ifBlank { stringResource(L10nR.string.feature_player_local_queue_c7d54b) }
}

@Composable
private fun repeatModeLabel(mode: EchoRepeatMode): String = when (mode) {
    EchoRepeatMode.Off -> stringResource(L10nR.string.feature_player_repeat_off_254ca2)
    EchoRepeatMode.All -> stringResource(L10nR.string.feature_player_repeat_all_b8cce1)
    EchoRepeatMode.One -> stringResource(L10nR.string.feature_player_repeat_one_3df94f)
}
