package app.echo.android.ui.shell

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
