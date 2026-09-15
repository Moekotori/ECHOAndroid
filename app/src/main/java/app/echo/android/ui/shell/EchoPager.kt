package app.echo.android.ui.shell

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
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

/** Keep page-swipe enabled while opening a library detail from Home; lock only after it settles. */
internal fun outerPagerUserScrollEnabled(
    libraryDetailOpen: Boolean,
    prefersLibrarySplit: Boolean,
    settledPage: Int,
    targetPage: Int,
    scrollInProgress: Boolean,
    innerTabPageSettled: Boolean,
): Boolean {
    if (innerTabPageSettled) return false
    if (!libraryDetailOpen || prefersLibrarySplit) return true
    val settledOnLibrary = settledPage == EchoPagerPage.Library.ordinal &&
        targetPage == EchoPagerPage.Library.ordinal
    return !settledOnLibrary || scrollInProgress
}

private const val NestedPagerDragFraction = 0.02f

/**
 * Nested album rows and clickable cards must receive the first pointer movement.
 * Only continue an in-progress page drag from pre-scroll once the pager has actually moved.
 */
@Composable
internal fun rememberHomeSafePagerNestedScroll(state: PagerState): NestedScrollConnection {
    val default = PagerDefaults.pageNestedScrollConnection(state, Orientation.Horizontal)
    return remember(state, default) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (state.innerTabsSettled()) return Offset.Zero
                if (state.currentPageOffsetFraction.absoluteValue < NestedPagerDragFraction) {
                    return Offset.Zero
                }
                return default.onPreScroll(available, source)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (state.innerTabsSettled()) return Offset.Zero
                return default.onPostScroll(consumed, available, source)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (state.innerTabsSettled()) return Velocity.Zero
                return default.onPreFling(available)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (state.innerTabsSettled()) return Velocity.Zero
                return default.onPostFling(consumed, available)
            }
        }
    }
}

private fun PagerState.innerTabsSettled(): Boolean {
    val page = settledPage
    return page == EchoPagerPage.Connect.ordinal || page == EchoPagerPage.Diagnostics.ordinal
}
