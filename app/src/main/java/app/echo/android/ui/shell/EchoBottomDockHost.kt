package app.echo.android.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.BottomDock
import app.echo.android.EchoAndroidViewModel
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoContentMaxWidth
import app.echo.android.design.EchoGlassInk
import app.echo.android.design.EchoGlassNight
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.EchoHomeMist
import app.echo.android.feature.player.MiniPlayer
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.PlaybackPositionState
import app.echo.android.model.settings.EchoEffectivePerformanceMode

private val DockMotionEasing = EchoMotion.Silk

@Composable
internal fun EchoBottomDockHost(
    viewModel: EchoAndroidViewModel,
    playbackStatus: EchoPlaybackStatus,
    darkTheme: Boolean,
    bottomDockExpanded: Boolean,
    effectivePerformanceMode: EchoEffectivePerformanceMode,
    onPlayPause: () -> Unit,
    onHideDock: () -> Unit,
    onShowDock: () -> Unit,
    onSelectTab: (Int) -> Unit,
    onExpand: () -> Unit,
    onOpenQueue: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
    selectedTab: State<Int>,
) {
    // 传 State 引用而非值:进度 tick 不重组整个底栏,由 MiniPlayer 进度条绘制期读取
    val playbackPosition = viewModel.playbackPosition.collectAsStateWithLifecycle()
    // selectedTab 只在 dock 作用域内读取:改动时仅底栏重组,不触碰整页内容
    val selectedTabValue = selectedTab.value
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = bottomDockExpanded,
            transitionSpec = {
                if (effectivePerformanceMode.isLightweight) {
                    fadeIn(tween(durationMillis = motionDuration(90, effectivePerformanceMode))) togetherWith
                        fadeOut(tween(durationMillis = motionDuration(90, effectivePerformanceMode)))
                } else {
                    val enter = fadeIn(
                        tween(
                            durationMillis = motionDuration(220, effectivePerformanceMode),
                            delayMillis = 70,
                            easing = DockMotionEasing,
                        ),
                    ) +
                        slideInVertically(EchoMotion.silkOffset(motionDuration(460, effectivePerformanceMode))) { height -> height / 3 } +
                        scaleIn(
                            initialScale = 0.96f,
                            animationSpec = EchoMotion.silkFloat(motionDuration(460, effectivePerformanceMode)),
                        )
                    val exit = fadeOut(tween(durationMillis = motionDuration(150, effectivePerformanceMode), easing = DockMotionEasing)) +
                        slideOutVertically(EchoMotion.silkOffset(motionDuration(260, effectivePerformanceMode))) { height -> height / 5 } +
                        scaleOut(
                            targetScale = 0.985f,
                            animationSpec = EchoMotion.silkFloat(motionDuration(260, effectivePerformanceMode)),
                        )
                    enter togetherWith exit
                }
            },
            label = "bottom-controls-transition",
        ) { expanded ->
            if (expanded) {
                ExpandedBottomControls(
                    status = playbackStatus,
                    positionState = playbackPosition,
                    darkTheme = darkTheme,
                    selectedTab = selectedTabValue,
                    selectedTabProgress = { selectedTabValue.toFloat() },
                    selectedTabProgressLive = false,
                    onPlayPause = onPlayPause,
                    onHideDock = onHideDock,
                    onSelectTab = onSelectTab,
                    onExpand = onExpand,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                CompactBottomControls(
                    status = playbackStatus,
                    positionState = playbackPosition,
                    darkTheme = darkTheme,
                    onPlayPause = onPlayPause,
                    onShowDock = onShowDock,
                    onOpenQueue = onOpenQueue,
                    onExpand = onExpand,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    modifier = Modifier
                        .widthIn(max = LocalEchoContentMaxWidth.current)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ExpandedBottomControls(
    status: EchoPlaybackStatus,
    positionState: State<PlaybackPositionState>,
    darkTheme: Boolean,
    selectedTab: Int,
    selectedTabProgress: () -> Float,
    selectedTabProgressLive: Boolean,
    onPlayPause: () -> Unit,
    onHideDock: () -> Unit,
    onSelectTab: (Int) -> Unit,
    onExpand: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.background(
            if (darkTheme) {
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        EchoGlassNight.copy(alpha = 0.28f),
                        EchoGlassInk.copy(alpha = 0.78f),
                        EchoGlassPanel.copy(alpha = 0.94f),
                    ),
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        EchoHomeMist.copy(alpha = 0.78f),
                        EchoHomeMist.copy(alpha = 0.98f),
                    ),
                )
            },
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        MiniPlayer(
            status = status,
            positionState = positionState,
            onPlayPause = onPlayPause,
            onHideDock = onHideDock,
            onExpand = onExpand,
            onNext = onNext,
            onPrevious = onPrevious,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .fillMaxWidth(),
        )
        BottomDock(
            selectedTab = selectedTab,
            selectedTabProgress = selectedTabProgress,
            progressLive = selectedTabProgressLive,
            onLightSurface = !darkTheme,
            onSelectTab = onSelectTab,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CompactBottomControls(
    status: EchoPlaybackStatus,
    positionState: State<PlaybackPositionState>,
    darkTheme: Boolean,
    onPlayPause: () -> Unit,
    onShowDock: () -> Unit,
    onOpenQueue: () -> Unit,
    onExpand: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                if (darkTheme) {
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            EchoGlassNight.copy(alpha = 0.26f),
                            EchoGlassInk.copy(alpha = 0.76f),
                            EchoGlassPanel.copy(alpha = 0.92f),
                        ),
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            EchoHomeMist.copy(alpha = 0.82f),
                        ),
                    )
                },
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        MiniPlayer(
            status = status,
            positionState = positionState,
            onPlayPause = onPlayPause,
            onShowDock = onShowDock,
            onOpenQueue = onOpenQueue,
            onExpand = onExpand,
            onNext = onNext,
            onPrevious = onPrevious,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
