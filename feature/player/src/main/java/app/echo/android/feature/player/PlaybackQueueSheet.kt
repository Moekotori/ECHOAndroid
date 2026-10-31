package app.echo.android.feature.player

import app.echo.android.feature.player.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import app.echo.android.design.EchoIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoTheme
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.playback.PlaybackQueueState
import kotlinx.coroutines.launch


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
    onClearNextUp: () -> Unit,
    onSetRepeatMode: (EchoRepeatMode) -> Unit,
    onToggleShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    onDragProgress: (Float) -> Unit = {},
    predictiveBackProgress: () -> Float = { 0f },
    onHidden: () -> Unit = {},
    onSetSleepTimerEndOfTrack: (() -> Unit)? = null,
    onCancelSleepTimer: (() -> Unit)? = null,
) {
    val presentation = remember { MutableTransitionState(false) }
    presentation.targetState = visible
    val hidden = rememberUpdatedState(onHidden)
    LaunchedEffect(presentation.isIdle, presentation.currentState, visible) {
        if (presentation.isIdle && !presentation.currentState && !visible) hidden.value()
    }
    AnimatedVisibility(
        visibleState = presentation,
        enter = EnterTransition.None,
        exit = ExitTransition.None,
        modifier = modifier,
    ) {
        val dark = LocalEchoDarkTheme.current
        val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
        val dragOffset = remember { NowPlayingDismissDragState() }
        val dragScope = rememberCoroutineScope()
        val density = LocalDensity.current
        val dismissThresholdPx = remember(density) { with(density) { 92.dp.toPx() } }
        val reportDrag by rememberUpdatedState(onDragProgress)
        val active = rememberUpdatedState(visible)
        val dismiss = rememberUpdatedState(onDismiss)
        fun visualOffset() = dragOffset.offsetPx +
            predictiveBackProgress().coerceIn(0f, 1f) * dismissThresholdPx * 1.35f
        fun restoreDrag() {
            dragOffset.settleJob?.cancel()
            dragOffset.settleJob = dragScope.launch {
                restoreNowPlayingDismiss(dragOffset, lightweight = lightweight)
            }
        }
        LaunchedEffect(dragOffset, dismissThresholdPx) {
            try {
                snapshotFlow { (dragOffset.offsetPx / (dismissThresholdPx * 3f)).coerceIn(0f, 1f) }
                    .collect { reportDrag(it) }
            } finally {
                reportDrag(0f)
            }
        }
        val scrimTargetAlpha = if (dark) 0.68f else 0.24f
        val scrimAlpha = transition.animateFloat(
            transitionSpec = {
                if (lightweight) {
                    tween(durationMillis = 90)
                } else if (targetState == EnterExitState.Visible) {
                    EchoMotion.silkFloat(280)
                } else {
                    EchoMotion.silkFloat(180)
                }
            },
            label = "queue-sheet-scrim",
        ) { state ->
            if (state == EnterExitState.Visible) scrimTargetAlpha else 0f
        }
        // 弹簧驱动:半路打断(快速开关)时速度连续,不会出现 tween 重启的顿挫
        val sheetProgress = transition.animateFloat(
            transitionSpec = {
                if (lightweight) {
                    tween(durationMillis = 90)
                } else if (targetState == EnterExitState.Visible) {
                    EchoMotion.silkFloat(420)
                } else {
                    EchoMotion.silkFloat(320)
                }
            },
            label = "queue-sheet-progress",
        ) { state ->
            if (state == EnterExitState.Visible) 1f else 0f
        }
        val contentProgress = transition.animateFloat(
            transitionSpec = {
                if (lightweight) {
                    tween(durationMillis = 90)
                } else if (targetState == EnterExitState.Visible) {
                    EchoMotion.silkFloat(380)
                } else {
                    EchoMotion.silkFloat(200)
                }
            },
            label = "queue-sheet-content",
        ) { state ->
            if (state == EnterExitState.Visible) 1f else 0f
        }

        LaunchedEffect(visible) {
            if (visible && dragOffset.offsetPx > 0f) restoreDrag()
        }

        app.echo.android.design.EchoInteractionGate(active = visible, modifier = Modifier.fillMaxSize()) {
            val theme = echoTheme()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val scrim = Brush.verticalGradient(
                            listOf(
                                theme.night.copy(alpha = 0.58f),
                                theme.ink.copy(alpha = 0.42f),
                                theme.panel.copy(alpha = 0.50f),
                            ),
                        )
                        onDrawBehind {
                            val dragFade = 1f - 0.45f * (visualOffset() / (dismissThresholdPx * 3f)).coerceIn(0f, 1f)
                            drawRect(scrim, alpha = scrimAlpha.value * dragFade)
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            QueueSheetSurface(
                status = status,
                queueState = queueState,
                motionProgress = { contentProgress.value },
                onDismiss = onDismiss,
                onPlayItem = onPlayItem,
                onRemoveItem = onRemoveItem,
                onMoveItem = onMoveItem,
                onClearQueue = onClearQueue,
                onClearNextUp = onClearNextUp,
                onSetRepeatMode = onSetRepeatMode,
                onToggleShuffle = onToggleShuffle,
                onSetSleepTimerEndOfTrack = onSetSleepTimerEndOfTrack,
                onCancelSleepTimer = onCancelSleepTimer,
                onHandleDrag = { delta ->
                    if (active.value) dragOffset.applyDelta(delta, dismissThresholdPx) {}
                },
                onHandleDragEnd = {
                    dragOffset.finishDrag()
                    if (active.value) {
                        if (dragOffset.offsetPx >= dismissThresholdPx) dismiss.value()
                        else restoreDrag()
                    }
                },
                onHandleDragCancel = {
                    dragOffset.finishDrag()
                    if (active.value) restoreDrag()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        val progress = sheetProgress.value.coerceIn(0f, 1f)
                        val hiddenProgress = 1f - progress
                        translationY = (if (lightweight) 0f else size.height * hiddenProgress) + visualOffset()
                        alpha = progress
                        scaleX = if (lightweight) 1f else 0.985f + 0.015f * progress
                        scaleY = if (lightweight) 1f else 0.992f + 0.008f * progress
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
    onClearNextUp: () -> Unit,
    onSetRepeatMode: (EchoRepeatMode) -> Unit,
    onToggleShuffle: () -> Unit,
    motionProgress: () -> Float,
    onSetSleepTimerEndOfTrack: (() -> Unit)?,
    onCancelSleepTimer: (() -> Unit)?,
    onHandleDrag: (Float) -> Unit,
    onHandleDragEnd: () -> Unit,
    onHandleDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val empty = queueState.items.isEmpty()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val contentLiftPx = with(LocalDensity.current) { 16.dp.toPx() }
    val sections = remember(queueState) { queueSections(queueState) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showPrevious by rememberSaveable { mutableStateOf(false) }
    val shortWindow = app.echo.android.design.LocalEchoLayout.current.heightDp < 600f
    val compact = sections.nextUp.isEmpty() && sections.continuation.isEmpty() && !showPrevious

    Box(
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .statusBarsPadding()
            .then(if (lightweight) Modifier else Modifier.animateContentSize(
                animationSpec = EchoMotion.silkSize(360), alignment = Alignment.BottomCenter,
            ))
            .then(if (empty) Modifier.wrapContentHeight() else Modifier.fillMaxHeight(if (shortWindow) 0.94f else if (compact) 0.42f else 0.74f))
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(queueSurfaceColor())
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (empty) Modifier else Modifier.fillMaxSize())
                .graphicsLayer {
                    val progress = motionProgress()
                    alpha = 0.6f + 0.4f * progress
                    translationY = if (lightweight) 0f else contentLiftPx * (1f - progress)
                }
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 72.dp, height = 32.dp)
                    .queueSheetHandleDrag(
                        onDrag = onHandleDrag,
                        onDragEnd = onHandleDragEnd,
                        onDragCancel = onHandleDragCancel,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 42.dp, height = 5.dp).clip(CircleShape)
                    .background(app.echo.android.design.echoIconColor().copy(alpha = 0.22f)))
            }
            QueueSheetHeader(
                queueState = queueState,
                status = status,
                onDismiss = onDismiss,
                onClearQueue = onClearQueue,
                sections = sections,
                showPrevious = showPrevious,
                onTogglePrevious = { showPrevious = !showPrevious },
                onLocateCurrent = {
                    scope.launch {
                        if (lightweight) listState.scrollToItem(0) else listState.animateScrollToItem(0)
                    }
                },
                onClearNextUp = onClearNextUp,
                onSetSleepTimerEndOfTrack = onSetSleepTimerEndOfTrack,
                onCancelSleepTimer = onCancelSleepTimer,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QueuePlaybackModeControl(
                    repeatMode = status.repeatMode,
                    shuffleEnabled = status.shuffleEnabled,
                    onSetRepeatMode = onSetRepeatMode,
                    onToggleShuffle = onToggleShuffle,
                )
                if (sections.nextUp.size > 1 || sections.continuation.size > 1) Text(
                    stringResource(L10nR.string.queue_reorder_hint), Modifier.weight(1f),
                    color = app.echo.android.design.echoIconColor().copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
            if (queueState.items.isEmpty()) {
                QueueEmptyState()
            } else {
                NextUpQueueList(
                    queueState, status.isPlaying, onPlayItem, onRemoveItem, onMoveItem,
                    sections, listState, showPrevious, { showPrevious = !showPrevious },
                )
            }
        }
    }
}

@Composable
private fun Modifier.queueSheetHandleDrag(
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
): Modifier {
    val drag = rememberUpdatedState(onDrag)
    val end = rememberUpdatedState(onDragEnd)
    val cancel = rememberUpdatedState(onDragCancel)
    return pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = { drag.value(0f) },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                drag.value(dragAmount)
            },
            onDragCancel = { cancel.value() },
            onDragEnd = { end.value() },
        )
    }
}

@Composable
private fun QueueEmptyState() {
    val titleColor = app.echo.android.design.echoIconColor()
    val mutedColor = titleColor.copy(alpha = 0.58f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EchoIcon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = titleColor,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(L10nR.string.feature_player_queue_is_empty_1b4178),
            color = titleColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
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
