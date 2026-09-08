package app.echo.android.design

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

object EchoMotion {
    const val FeedbackMs = 180
    const val FadeMs = 180
    const val FadeExitMs = 120
    const val CollapseMs = 260
    const val PressedScale = 0.97f

    fun stateChange(): ContentTransform = ContentTransform(
        targetContentEnter = fadeIn(tween(FadeMs, easing = Silk)),
        initialContentExit = fadeOut(tween(FadeExitMs, easing = SilkExit)),
        sizeTransform = null,
    )

    val Silk: Easing = CubicBezierEasing(0.22f, 1.00f, 0.36f, 1.00f)
    val SilkExit: Easing = CubicBezierEasing(0.32f, 0.00f, 0.18f, 1.00f)

    const val OverlayMs = 480
    const val OverlayExitMs = 340
    const val OverlayFadeMs = 280
    const val PageMs = 460
    const val PageExitMs = 300
    const val TabMs = 380
    const val ExpandMs = 360

    // 位移/缩放统一走临界阻尼弹簧:重定向与打断时速度连续,不会出现 tween 重启的顿挫。
    // 把原 tween 时长映射成大致等感的刚度(约 ms≈400 -> stiffness≈400)。
    fun silkStiffness(ms: Int): Float {
        val omega = 8000f / ms.coerceAtLeast(140)
        return omega * omega
    }

    fun silkFloat(ms: Int): SpringSpec<Float> =
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = silkStiffness(ms),
            visibilityThreshold = 0.0008f,
        )

    fun silkOffset(ms: Int): SpringSpec<IntOffset> =
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = silkStiffness(ms),
            visibilityThreshold = IntOffset.VisibilityThreshold,
        )

    fun silkDp(ms: Int): SpringSpec<Dp> =
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = silkStiffness(ms),
            visibilityThreshold = Dp.VisibilityThreshold,
        )

    fun silkSize(ms: Int): SpringSpec<IntSize> =
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = silkStiffness(ms),
            visibilityThreshold = IntSize.VisibilityThreshold,
        )

    fun overlayEnter(
        enterMs: Int = OverlayMs,
        fadeMs: Int = OverlayFadeMs,
    ): EnterTransition =
        fadeIn(tween(durationMillis = fadeMs, easing = Silk)) +
            slideInVertically(silkOffset(enterMs)) { it / 10 } +
            scaleIn(
                initialScale = 0.975f,
                animationSpec = silkFloat(enterMs),
            )

    fun overlayExit(
        exitMs: Int = OverlayExitMs,
        fadeMs: Int = 180,
    ): ExitTransition =
        fadeOut(tween(durationMillis = fadeMs, easing = SilkExit)) +
            slideOutVertically(silkOffset(exitMs)) { it / 12 } +
            scaleOut(
                targetScale = 0.975f,
                animationSpec = silkFloat(exitMs),
            )

    fun nowPlayingEnter(
        enterMs: Int = 520,
        fadeMs: Int = 260,
    ): EnterTransition =
        fadeIn(tween(durationMillis = fadeMs, delayMillis = 24, easing = Silk)) +
            slideInVertically(silkOffset(enterMs)) { it } +
            scaleIn(
                initialScale = 0.96f,
                animationSpec = silkFloat(enterMs),
            )

    fun nowPlayingExit(
        exitMs: Int = 380,
        fadeMs: Int = 200,
    ): ExitTransition =
        fadeOut(tween(durationMillis = fadeMs, easing = SilkExit)) +
            slideOutVertically(silkOffset(exitMs)) { it } +
            scaleOut(
                targetScale = 0.97f,
                animationSpec = silkFloat(exitMs),
            )

    fun dialogEnter(
        enterMs: Int = 420,
        fadeMs: Int = 240,
    ): EnterTransition =
        fadeIn(tween(durationMillis = fadeMs, easing = Silk)) +
            scaleIn(
                initialScale = 0.94f,
                animationSpec = silkFloat(enterMs),
            )

    fun dialogExit(
        exitMs: Int = 240,
        fadeMs: Int = 160,
    ): ExitTransition =
        fadeOut(tween(durationMillis = fadeMs, easing = SilkExit)) +
            scaleOut(
                targetScale = 0.96f,
                animationSpec = silkFloat(exitMs),
            )

    fun pagePush(
        enterMs: Int = PageMs,
        exitMs: Int = PageExitMs,
    ): ContentTransform {
        val enter = slideInHorizontally(silkOffset(enterMs)) { it / 5 } +
            fadeIn(tween(durationMillis = 240, easing = Silk)) +
            scaleIn(
                initialScale = 0.978f,
                animationSpec = silkFloat(enterMs),
            )
        val exit = slideOutHorizontally(silkOffset(exitMs)) { -it / 8 } +
            fadeOut(tween(durationMillis = 180, easing = SilkExit)) +
            scaleOut(
                targetScale = 0.992f,
                animationSpec = silkFloat(exitMs),
            )
        return ContentTransform(
            targetContentEnter = enter,
            initialContentExit = exit,
            targetContentZIndex = 1f,
            sizeTransform = SizeTransform(clip = false),
        )
    }

    fun pagePop(
        enterMs: Int = PageMs,
        exitMs: Int = PageMs,
    ): ContentTransform {
        val enter = slideInHorizontally(silkOffset(enterMs)) { -it / 8 } +
            fadeIn(tween(durationMillis = 240, delayMillis = 36, easing = Silk)) +
            scaleIn(
                initialScale = 0.99f,
                animationSpec = silkFloat(enterMs),
            )
        val exit = slideOutHorizontally(silkOffset(exitMs)) { it / 5 } +
            fadeOut(tween(durationMillis = 200, easing = SilkExit)) +
            scaleOut(
                targetScale = 0.978f,
                animationSpec = silkFloat(exitMs),
            )
        return ContentTransform(
            targetContentEnter = enter,
            initialContentExit = exit,
            targetContentZIndex = 0f,
            sizeTransform = SizeTransform(clip = false),
        )
    }

    fun tabSwitch(
        forward: Boolean,
        durationMs: Int = TabMs,
    ): ContentTransform {
        val direction = if (forward) 1 else -1
        val enter = slideInHorizontally(silkOffset(durationMs)) { direction * it / 9 } +
            fadeIn(tween(durationMillis = 220, easing = Silk)) +
            scaleIn(
                initialScale = 0.988f,
                animationSpec = silkFloat(durationMs),
            )
        val exit = slideOutHorizontally(silkOffset(durationMs - 80)) { -direction * it / 11 } +
            fadeOut(tween(durationMillis = 160, easing = SilkExit))
        return ContentTransform(
            targetContentEnter = enter,
            initialContentExit = exit,
            sizeTransform = SizeTransform(clip = false),
        )
    }
}
