package app.echo.android.ui.shell

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import app.echo.android.EchoTab
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

private val RouteMotionEasing = CubicBezierEasing(0.18f, 0.86f, 0.20f, 1f)
private const val ROUTE_MOTION_BASE_DURATION_MS = 150
private const val ROUTE_MOTION_DISTANCE_DURATION_MS = 18
private const val ROUTE_MOTION_MAX_DURATION_MS = 220

internal fun routeMotionSpec(
    fromPage: Int,
    toPage: Int,
    effectivePerformanceMode: EchoEffectivePerformanceMode,
): AnimationSpec<Float> {
    val distance = (toPage - fromPage).absoluteValue.coerceAtLeast(1)
    val duration = (ROUTE_MOTION_BASE_DURATION_MS + (distance - 1) * ROUTE_MOTION_DISTANCE_DURATION_MS)
        .coerceAtMost(ROUTE_MOTION_MAX_DURATION_MS)
        .let { motionDuration(it, effectivePerformanceMode) }
    return tween(durationMillis = duration, easing = RouteMotionEasing)
}

internal fun motionDuration(defaultMs: Int, effectivePerformanceMode: EchoEffectivePerformanceMode): Int =
    when {
        effectivePerformanceMode.isLightweight -> (defaultMs * 0.20f).roundToInt().coerceIn(45, 120)
        effectivePerformanceMode.isHighPerformance -> defaultMs
        else -> (defaultMs * 0.72f).roundToInt().coerceIn(110, defaultMs)
    }
