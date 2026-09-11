package app.echo.android.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.BottomDock
import app.echo.android.EchoAndroidViewModel
import app.echo.android.EchoTab
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoContentMaxWidth
import app.echo.android.design.echoTheme
import app.echo.android.feature.player.MiniPlayer
import app.echo.android.model.playback.EchoPlaybackStatus
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
    val dockMotionDuration = motionDuration(420, effectivePerformanceMode)
    // A non-bouncy spring preserves velocity when the user reverses the toggle mid-motion.
    val dockSizeMotion = if (effectivePerformanceMode.isLightweight) {
        tween<IntSize>(dockMotionDuration, easing = DockMotionEasing)
    } else {
        spring<IntSize>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = EchoMotion.silkStiffness(dockMotionDuration),
            visibilityThreshold = IntSize(1, 1),
        )
    }
    val dockFadeMotion = if (effectivePerformanceMode.isLightweight) {
        tween<Float>(dockMotionDuration, easing = DockMotionEasing)
    } else {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = EchoMotion.silkStiffness(dockMotionDuration),
            visibilityThreshold = 0.01f,
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    if (darkTheme) {
                        val theme = echoTheme()
                        listOf(Color.Transparent, theme.night.copy(alpha = 0.40f), theme.panel.copy(alpha = 0.94f))
                    } else {
                        listOf(Color.Transparent, echoTheme().mist.copy(alpha = 0.96f))
                    },
                ),
            )
            .navigationBarsPadding()
            .padding(top = 6.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Keep the player mounted: expanding navigation must not restart artwork or gestures.
        MiniPlayer(
            status = playbackStatus,
            positionState = playbackPosition,
            onPlayPause = onPlayPause,
            onHideDock = if (bottomDockExpanded) onHideDock else null,
            onShowDock = if (bottomDockExpanded) null else onShowDock,
            onOpenQueue = onOpenQueue,
            onExpand = onExpand,
            onNext = onNext,
            onPrevious = onPrevious,
            modifier = Modifier
                .widthIn(max = LocalEchoContentMaxWidth.current)
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
        )
        // Reveal from the bottom so navigation stays anchored while the player moves above it.
        AnimatedVisibility(
            visible = bottomDockExpanded,
            enter = expandVertically(
                expandFrom = Alignment.Bottom,
                animationSpec = dockSizeMotion,
            ) + fadeIn(dockFadeMotion),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Bottom,
                animationSpec = dockSizeMotion,
            ) + fadeOut(dockFadeMotion),
        ) {
            BottomDock(
                selectedTab = selectedTab,
                selectedTabProgress = dockTabProgress,
                progressLive = pagerState.isScrollInProgress,
                onLightSurface = !darkTheme,
                onSelectTab = onSelectTab,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
