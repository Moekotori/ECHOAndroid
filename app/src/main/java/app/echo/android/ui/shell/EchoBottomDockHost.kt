package app.echo.android.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.BottomDock
import app.echo.android.EchoAndroidViewModel
import app.echo.android.EchoTab
import app.echo.android.design.LocalEchoContentMaxWidth
import app.echo.android.design.EchoGlassInk
import app.echo.android.design.EchoGlassNight
import app.echo.android.design.EchoGlassPanel
import app.echo.android.feature.player.MiniPlayer
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.PlaybackPositionState
import app.echo.android.model.settings.EchoEffectivePerformanceMode

private val DockMotionEasing = CubicBezierEasing(0.16f, 1f, 0.30f, 1f)

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
    val playbackPosition by viewModel.playbackPosition.collectAsStateWithLifecycle()
    val dockTabProgress = (
        pagerState.currentPage +
            pagerState.currentPageOffsetFraction -
            EchoPagerPage.Now.ordinal
        ).coerceIn(0f, EchoTab.entries.lastIndex.toFloat())
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
                        slideInVertically(tween(durationMillis = motionDuration(460, effectivePerformanceMode), easing = DockMotionEasing)) { height -> height / 3 } +
                        scaleIn(
                            initialScale = 0.96f,
                            animationSpec = tween(durationMillis = motionDuration(460, effectivePerformanceMode), easing = DockMotionEasing),
                        )
                    val exit = fadeOut(tween(durationMillis = motionDuration(150, effectivePerformanceMode), easing = DockMotionEasing)) +
                        slideOutVertically(tween(durationMillis = motionDuration(260, effectivePerformanceMode), easing = DockMotionEasing)) { height -> height / 5 } +
                        scaleOut(
                            targetScale = 0.985f,
                            animationSpec = tween(durationMillis = motionDuration(260, effectivePerformanceMode), easing = DockMotionEasing),
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
                    selectedTab = selectedTab,
                    selectedTabProgress = dockTabProgress,
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
    positionState: PlaybackPositionState,
    darkTheme: Boolean,
    selectedTab: Int,
    selectedTabProgress: Float,
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
                        Color(0xFFEAF2FF).copy(alpha = 0.78f),
                        Color(0xFFEAF2FF).copy(alpha = 0.98f),
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
            onLightSurface = !darkTheme,
            onSelectTab = onSelectTab,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CompactBottomControls(
    status: EchoPlaybackStatus,
    positionState: PlaybackPositionState,
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
                            Color(0xFFEAF2FF).copy(alpha = 0.82f),
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
