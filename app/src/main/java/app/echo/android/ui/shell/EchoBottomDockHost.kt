package app.echo.android.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.BottomDock
import app.echo.android.EchoAndroidViewModel
import app.echo.android.EchoTab
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
    pagerState: PagerState,
    playbackStatus: EchoPlaybackStatus,
    darkTheme: Boolean,
    selectedTab: Int,
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
) {
    // 传 State 引用而非值:进度 tick 不重组整个底栏,由 MiniPlayer 进度条绘制期读取
    val playbackPosition = viewModel.playbackPosition.collectAsStateWithLifecycle()
    // 以 lambda 延迟读取 pager 偏移,滑动时只更新指示条自身,不重组整个底栏
    val dockTabProgress = remember(pagerState) {
        {
            (
                pagerState.currentPage +
                    pagerState.currentPageOffsetFraction -
                    EchoPagerPage.Now.ordinal
                ).coerceIn(0f, EchoTab.entries.lastIndex.toFloat())
        }
    }
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
                    val duration = motionDuration(300, effectivePerformanceMode)
                    val enter = fadeIn(tween(180, delayMillis = 60, easing = DockMotionEasing)) +
                        slideInVertically(tween(duration, easing = DockMotionEasing)) { it / 8 }
                    val exit = fadeOut(tween(120, easing = DockMotionEasing)) +
                        slideOutVertically(tween(180, easing = DockMotionEasing)) { it / 10 }
                    (enter togetherWith exit).using(
                        SizeTransform { _, _ -> tween(duration, easing = DockMotionEasing) },
                    )
                }
            },
            contentAlignment = Alignment.BottomCenter,
            label = "bottom-controls-transition",
        ) { expanded ->
            if (expanded) {
                ExpandedBottomControls(
                    status = playbackStatus,
                    positionState = playbackPosition,
                    darkTheme = darkTheme,
                    selectedTab = selectedTab,
                    selectedTabProgress = dockTabProgress,
                    selectedTabProgressLive = pagerState.isScrollInProgress,
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
