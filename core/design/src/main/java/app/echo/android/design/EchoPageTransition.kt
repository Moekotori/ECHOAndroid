package app.echo.android.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Page identity, rather than changing data, owns the transition and composition lifetime. */
@Composable
fun <T> EchoPageContent(
    targetState: T,
    contentKey: (T) -> String,
    isBackward: (initial: T, target: T) -> Boolean,
    modifier: Modifier = Modifier,
    fadeOnly: Boolean = false,
    label: String = "echo-page",
    transitionSpec: (AnimatedContentTransitionScope<T>.() -> ContentTransform)? = null,
    content: @Composable (T) -> Unit,
) {
    val motion = rememberEchoContentMotion()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val navigation = updateTransition(targetState, label = label)
    val returning = isBackward(navigation.currentState, navigation.targetState)
    navigation.AnimatedContent(
        contentKey = contentKey,
        modifier = modifier,
        transitionSpec = transitionSpec ?: {
            when {
                fadeOnly -> motion.pageFade()
                isBackward(initialState, this.targetState) -> motion.pagePop()
                else -> motion.pagePush()
            }
        },
    ) { page ->
        val corners = transition.animateFloat(
            transitionSpec = { if (lightweight || fadeOnly) tween(0) else EchoMotion.silkFloat(EchoMotion.PageMs) },
            label = "page-corners",
        ) { if (it == EnterExitState.Visible) 0f else 1f }
        val reveal = transition.animateFloat(
            transitionSpec = { tween(if (lightweight || fadeOnly) 0 else EchoPageRevealPolicy.TimelineMs, easing = LinearEasing) },
            label = "page-sections",
        ) { if (!returning && it == EnterExitState.PreEnter) 0f else 1f }
        EchoPageSurface(
            active = contentKey(page) == contentKey(targetState),
            modifier = if (lightweight || fadeOnly) Modifier else Modifier.graphicsLayer {
                val cornerProgress = corners.value.coerceIn(0f, 1f)
                shape = RoundedCornerShape((EchoMotion.PageCornerDp * cornerProgress).dp)
                clip = cornerProgress > 0.001f
            },
        ) {
            EchoPageRevealProvider(reveal, enabled = !lightweight && !fadeOnly) { content(page) }
        }
    }
}

/** Keep the outgoing page mounted until exit completes, then let Compose dispose it. */
@Composable
fun EchoPageOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    onHidden: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val motion = rememberEchoContentMotion()
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = visible
    val hiddenCallback = rememberUpdatedState(onHidden)
    LaunchedEffect(visibility.isIdle, visibility.currentState, visible) {
        if (visibility.isIdle && !visibility.currentState && !visible) hiddenCallback.value()
    }
    AnimatedVisibility(
        visibleState = visibility,
        modifier = modifier,
        enter = motion.overlayEnter(),
        exit = motion.overlayExit(),
    ) {
        val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
        val reveal = transition.animateFloat(
            transitionSpec = { tween(if (lightweight) 0 else EchoPageRevealPolicy.TimelineMs, easing = LinearEasing) },
            label = "overlay-sections",
        ) { if (it == EnterExitState.PreEnter) 0f else 1f }
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            EchoPageSurface(active = visible) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }) {
                    EchoPageRevealProvider(reveal, enabled = !lightweight, content = content)
                }
            }
        }
    }
}

/** An exiting page remains drawable, but must not expose stale actions. */
@Composable
internal fun EchoPageSurface(
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.fillMaxSize().then(
            if (active) Modifier else Modifier
                .clearAndSetSemantics { }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                },
        ),
    ) { content() }
}

/** Keep the underlying page visible and retreat it while a full-page overlay covers it. */
@Composable
fun Modifier.echoPageUnderlay(obscured: Boolean): Modifier {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val depth = animateFloatAsState(
        targetValue = if (obscured && !lightweight) 1f else 0f,
        animationSpec = if (lightweight) tween(0) else EchoMotion.silkFloat(EchoMotion.PageMs),
        label = "echo-page-underlay",
    )
    return graphicsLayer {
        val progress = depth.value.coerceIn(0f, 1f)
        transformOrigin = TransformOrigin(0.5f, 0.12f)
        scaleX = 1f - (1f - EchoMotion.PageDepthScale) * progress
        scaleY = scaleX
        translationY = 8.dp.toPx() * progress
        alpha = 1f - (1f - EchoMotion.PageDepthAlpha) * progress
        shape = RoundedCornerShape((EchoMotion.PageCornerDp * progress).dp)
        clip = progress > 0.001f
    }
}
