package app.echo.android.ui.shell

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import app.echo.android.EchoTab
import app.echo.android.design.EchoMotion
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

internal enum class EchoPagerPage {
    Settings,
    Now,
    Library,
    Connect,
    Diagnostics,
}

internal val EchoTab.pagerPage: EchoPagerPage
    get() = when (this) {
        EchoTab.Now -> EchoPagerPage.Now
        EchoTab.Library -> EchoPagerPage.Library
        EchoTab.Connect -> EchoPagerPage.Connect
        EchoTab.Diagnostics -> EchoPagerPage.Diagnostics
    }

internal val EchoPagerPage.dockTab: EchoTab?
    get() = when (this) {
        EchoPagerPage.Now -> EchoTab.Now
        EchoPagerPage.Library -> EchoTab.Library
        EchoPagerPage.Connect -> EchoTab.Connect
        EchoPagerPage.Diagnostics -> EchoTab.Diagnostics
        EchoPagerPage.Settings -> null
    }

private const val ROUTE_MOTION_BASE_DURATION_MS = 420
private const val ROUTE_MOTION_DISTANCE_DURATION_MS = 48
private const val ROUTE_MOTION_MAX_DURATION_MS = 560

// Point navigation has a bounded finish; keep the spring below for touch flings.
internal fun dockNavigationMotionSpec(
    fromPage: Int,
    toPage: Int,
    effectivePerformanceMode: EchoEffectivePerformanceMode,
): AnimationSpec<Float> {
    val distance = (toPage - fromPage).absoluteValue.coerceAtLeast(1)
    val duration = when {
        effectivePerformanceMode.isLightweight -> 100
        else -> (340 + (distance - 1) * 35).coerceAtMost(440)
    }
    return tween(durationMillis = duration, easing = EchoMotion.Silk)
}

internal fun routeMotionSpec(
    fromPage: Int,
    toPage: Int,
    effectivePerformanceMode: EchoEffectivePerformanceMode,
): AnimationSpec<Float> {
    val distance = (toPage - fromPage).absoluteValue.coerceAtLeast(1)
    val duration = (ROUTE_MOTION_BASE_DURATION_MS + (distance - 1) * ROUTE_MOTION_DISTANCE_DURATION_MS)
        .coerceAtMost(ROUTE_MOTION_MAX_DURATION_MS)
        .let { motionDuration(it, effectivePerformanceMode) }
    // 弹簧:甩动松手继承手指速度,导航中途重定向也不会出现速度跳变
    return spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = EchoMotion.silkStiffness(duration),
        visibilityThreshold = 0.5f,
    )
}

internal fun motionDuration(defaultMs: Int, effectivePerformanceMode: EchoEffectivePerformanceMode): Int =
    when {
        effectivePerformanceMode.isLightweight -> (defaultMs * 0.20f).roundToInt().coerceIn(45, 120)
        effectivePerformanceMode.isHighPerformance -> defaultMs
        else -> (defaultMs * 0.72f).roundToInt().coerceIn(minOf(110, defaultMs), defaultMs)
    }

private fun PagerState.ownsInnerHorizontalTabs(): Boolean {
    val page = currentPage
    return page == EchoPagerPage.Connect.ordinal || page == EchoPagerPage.Diagnostics.ordinal
}

/**
 * Connect / Signal keep their own inner pagers. Leftover horizontal nested scroll at those
 * inner edges is the only path that should still move the dock pager.
 */
@Composable
internal fun rememberTabPagerNestedScrollConnection(
    state: PagerState,
    flingBehavior: TargetedFlingBehavior,
): NestedScrollConnection {
    val default = PagerDefaults.pageNestedScrollConnection(state, Orientation.Horizontal)
    return remember(state, default, flingBehavior) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                default.onPreScroll(available, source)

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput &&
                    state.ownsInnerHorizontalTabs() &&
                    available.x != 0f
                ) {
                    val consumedX = -state.dispatchRawDelta(-available.x)
                    return Offset(consumedX, 0f)
                }
                return default.onPostScroll(consumed, available, source)
            }

            override suspend fun onPreFling(available: Velocity): Velocity =
                default.onPreFling(available)

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!state.ownsInnerHorizontalTabs()) {
                    return default.onPostFling(consumed, available)
                }
                if (state.currentPageOffsetFraction.absoluteValue <= 0.001f &&
                    available.x.absoluteValue < 40f
                ) {
                    return default.onPostFling(consumed, available)
                }
                state.scroll {
                    with(flingBehavior) { performFling(-available.x) }
                }
                return available.copy(y = 0f)
            }
        }
    }
}
