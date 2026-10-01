package app.echo.android.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.updateTransition
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.echo.android.BottomDock
import app.echo.android.EchoAndroidViewModel
import app.echo.android.EchoTab
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoContentMaxWidth
import app.echo.android.design.echoChromeColors
import app.echo.android.design.rememberSilkPagerFlingBehavior
import app.echo.android.feature.player.MiniPlayer
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.PlaybackPositionState
import kotlinx.coroutines.flow.StateFlow
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
    animationsVisible: Boolean = true,
    showNavigation: Boolean = true,
    /** 投送到 DLNA / Chromecast 时换成远端进度。 */
    positionFlow: StateFlow<PlaybackPositionState> = viewModel.playbackPosition,
    abovePlayer: (@Composable () -> Unit)? = null,
    playerOverride: (@Composable () -> Unit)? = null,
) {
    // 传 State 引用而非值:进度 tick 不重组整个底栏,由 MiniPlayer 进度条绘制期读取
    val playbackPosition = if (playerOverride == null) positionFlow.collectAsStateWithLifecycle() else null
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
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val motionEnabled = animationsVisible && LocalWindowInfo.current.isWindowFocused &&
        lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val dockMotionDuration = motionDuration(360, effectivePerformanceMode)
    // A non-bouncy spring preserves velocity when the user reverses the toggle mid-motion.
    val dockSizeMotion = if (!motionEnabled) {
        snap<IntSize>()
    } else if (effectivePerformanceMode.isLightweight) {
        tween<IntSize>(dockMotionDuration, easing = DockMotionEasing)
    } else {
        spring<IntSize>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = EchoMotion.silkStiffness(dockMotionDuration),
            visibilityThreshold = IntSize(1, 1),
        )
    }
    val dockPhaseMotion = if (!motionEnabled) {
        snap<Float>()
    } else if (effectivePerformanceMode.isLightweight) {
        tween<Float>(dockMotionDuration, easing = DockMotionEasing)
    } else {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = EchoMotion.silkStiffness(dockMotionDuration),
            visibilityThreshold = 0.001f,
        )
    }
    val dockTransition = updateTransition(showNavigation && bottomDockExpanded, label = "bottom-dock")
    val dockExpansion = dockTransition.animateFloat(
        transitionSpec = { dockPhaseMotion }, label = "dock-expansion",
    ) { expanded -> if (expanded) 1f else 0f }
    val chrome = echoChromeColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(chrome.surface)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Keep the player mounted: expanding navigation must not restart artwork or gestures.
        abovePlayer?.invoke()
        if (playerOverride != null) playerOverride() else MiniPlayer(
            animationsVisible = motionEnabled,
            dockExpansion = dockExpansion,
            status = playbackStatus,
            positionState = requireNotNull(playbackPosition),
            onPlayPause = onPlayPause,
            onHideDock = if (showNavigation && bottomDockExpanded) onHideDock else null,
            onShowDock = if (showNavigation && !bottomDockExpanded) onShowDock else null,
            onOpenQueue = onOpenQueue,
            onExpand = onExpand,
            onNext = onNext,
            onPrevious = onPrevious,
            modifier = Modifier
                .widthIn(max = LocalEchoContentMaxWidth.current)
                .fillMaxWidth(),
        )
        // Reveal from the bottom so navigation stays anchored while the player moves above it.
        dockTransition.AnimatedVisibility(
            visible = { it },
            enter = expandVertically(
                expandFrom = Alignment.Bottom,
                animationSpec = dockSizeMotion,
            ),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Bottom,
                animationSpec = dockSizeMotion,
            ),
        ) {
            BottomDock(
                selectedTab = selectedTab,
                gestureModifier = rememberDockSwipeModifier(
                    pagerState,
                    rememberSilkPagerFlingBehavior(pagerState),
                ),
                selectedTabProgress = dockTabProgress,
                onSelectTab = onSelectTab,
                modifier = Modifier
                    .widthIn(max = LocalEchoContentMaxWidth.current)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val phase = dockExpansion.value.coerceIn(0f, 1f)
                        alpha = phase * phase * (3f - 2f * phase)
                        translationY = (1f - phase) * 4.dp.toPx()
                    },
            )
        }
    }
}
