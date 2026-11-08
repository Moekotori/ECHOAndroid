package app.echo.android.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.ExperimentalTransitionApi
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Where a back gesture is heading. While [progress] is above zero the page stack is scrubbed
 * towards [target]; the owner commits by changing the page state, or cancels by easing
 * [progress] back to zero.
 */
class EchoPageBack<T>(val target: T, val progress: () -> Float)

/** Page identity, rather than changing data, owns the transition and composition lifetime. */
@OptIn(ExperimentalTransitionApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun <T> EchoPageContent(
    targetState: T,
    contentKey: (T) -> String,
    isBackward: (initial: T, target: T) -> Boolean,
    modifier: Modifier = Modifier,
    fadeOnly: Boolean = false,
    label: String = "echo-page",
    transitionSpec: (AnimatedContentTransitionScope<T>.() -> ContentTransform)? = null,
    back: EchoPageBack<T>? = null,
    /** Hosts [echoSharedCover] flights between pages. The caller sizes the host through [modifier]. */
    sharedElements: Boolean = false,
    content: @Composable (T) -> Unit,
) {
    val motion = rememberEchoContentMotion()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    // The transition only ever sees one instance per page key (the anchor). A data refresh of
    // the same page (a live summary, a late-loaded album list) therefore compares equal and
    // cannot reset a flight in progress; SeekableTransitionState.animateTo restarts from zero
    // whenever the target state is not equal to the previous one. Fresh data travels in
    // [latest] instead, and is what [content] renders.
    val pageKey = contentKey(targetState)
    val anchors = remember { HashMap<String, T>() }
    val latest = remember { mutableStateMapOf<String, T>() }
    val anchor = anchors.getOrPut(pageKey) { targetState }
    if (Snapshot.withoutReadObservation { latest[pageKey] } != targetState) latest[pageKey] = targetState
    val seekable = remember { SeekableTransitionState(anchor) }
    // Navigation continues from wherever a back gesture left the transition.
    LaunchedEffect(pageKey) {
        seekable.animateTo(anchor)
        // Settled: only the visible page needs its snapshot kept.
        anchors.keys.retainAll { it == pageKey }
        latest.keys.retainAll { it == pageKey }
    }
    val backKey = back?.let { contentKey(it.target) }
    val latestBack = rememberUpdatedState(back)
    if (backKey != null) {
        LaunchedEffect(backKey, pageKey) {
            val gesture = latestBack.value ?: return@LaunchedEffect
            if (backKey == pageKey) return@LaunchedEffect
            var scrubbing = false
            snapshotFlow { gesture.progress() }.collect { progress ->
                if (progress > 0f) {
                    scrubbing = true
                    val destination = anchors.getOrPut(backKey) { gesture.target }
                    if (latest[backKey] == null) latest[backKey] = gesture.target
                    seekable.seekTo(progress.coerceIn(0f, 1f), destination)
                } else if (scrubbing) {
                    scrubbing = false
                    // Cancelled gestures have already eased to zero; a committed one is
                    // superseded by the navigation above before a frame can pass.
                    seekable.animateTo(seekable.currentState)
                }
            }
        }
    }
    val navigation = rememberTransition(seekable, label = label)
    val returning = isBackward(navigation.currentState, navigation.targetState)
    val pages: @Composable (Modifier) -> Unit = { pagesModifier ->
    navigation.AnimatedContent(
        contentKey = contentKey,
        modifier = pagesModifier,
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
        // Only the page that slides horizontally catches light: the incoming page on push,
        // the outgoing page on pop. It peaks mid-flight and is gone when the page settles.
        val slides = (transition.targetState == EnterExitState.PostExit) == returning
        val dark = LocalEchoDarkTheme.current
        val tint = MaterialTheme.colorScheme.primary
        val pageScope: AnimatedVisibilityScope = this
        CompositionLocalProvider(LocalEchoPageVisibilityScope provides pageScope) {
        EchoPageSurface(
            active = contentKey(page) == pageKey,
            modifier = if (lightweight || fadeOnly) Modifier else Modifier
                .graphicsLayer {
                    val cornerProgress = corners.value.coerceIn(0f, 1f)
                    shape = RoundedCornerShape((EchoMotion.PageCornerDp * cornerProgress).dp)
                    clip = cornerProgress > 0.001f
                }
                // The page being covered (or uncovered) darkens; the one that slides catches light.
                .echoPageDim({ if (slides) 0f else corners.value }, dark)
                .echoPageEdgeLight(
                    light = {
                        val progress = corners.value.coerceIn(0f, 1f)
                        if (slides) 4f * progress * (1f - progress) else 0f
                    },
                    tint = tint,
                    dark = dark,
                ),
        ) {
            EchoPageRevealProvider(reveal, enabled = !lightweight && !fadeOnly) {
                content(latest[contentKey(page)] ?: page)
            }
        }
        }
    }
    }
    if (sharedElements && !lightweight) {
        SharedTransitionLayout(modifier) {
            CompositionLocalProvider(LocalEchoPageSharedScope provides this) { pages(Modifier.fillMaxSize()) }
        }
    } else {
        pages(modifier)
    }
}

/** Keep the outgoing page mounted until exit completes, then let Compose dispose it. */
@Composable
fun EchoPageOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    onHidden: () -> Unit = {},
    backProgress: () -> Float = { 0f },
    interactive: Boolean = true,
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
            EchoPageSurface(
                active = visible && interactive,
                modifier = Modifier.echoPageBackLayer(lightweight, backProgress, LocalEchoDarkTheme.current),
            ) {
                EchoLayoutBounds(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
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
    EchoInteractionGate(active, modifier.fillMaxSize()) { content() }
}

/** The covering page follows the back gesture; progress is read in the draw phase only. */
private fun Modifier.echoPageBackLayer(lightweight: Boolean, backProgress: () -> Float, dark: Boolean): Modifier =
    if (lightweight) this else this
        .graphicsLayer {
            val progress = backProgress().coerceIn(0f, 1f)
            scaleX = 1f - EchoMotion.PageBackScaleDrop * progress
            scaleY = scaleX
            shape = RoundedCornerShape((EchoMotion.PageBackCornerDp * progress).dp)
            clip = progress > 0.001f
        }
        .echoPageRimLight(backProgress, EchoMotion.PageBackCornerDp, dark)

/**
 * Keep the underlying page visible and retreat it while a full-page overlay covers it.
 * [backProgress] hands the page back as the covering overlay is dragged away.
 */
@Composable
fun Modifier.echoPageUnderlay(obscured: Boolean, backProgress: () -> Float = { 0f }): Modifier {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val depth = animateFloatAsState(
        targetValue = if (obscured && !lightweight) 1f else 0f,
        animationSpec = if (lightweight) tween(0) else EchoMotion.silkFloat(EchoMotion.PageMs),
        label = "echo-page-underlay",
    )
    val dark = LocalEchoDarkTheme.current
    fun progress() = depth.value.coerceIn(0f, 1f) * (1f - backProgress().coerceIn(0f, 1f))
    return graphicsLayer {
        val progress = progress()
        transformOrigin = TransformOrigin(0.5f, 0.12f)
        scaleX = 1f - (1f - EchoMotion.PageDepthScale) * progress
        scaleY = scaleX
        translationY = 8.dp.toPx() * progress
        shape = RoundedCornerShape((EchoMotion.PageCornerDp * progress).dp)
        clip = progress > 0.001f
    }.echoPageDim(::progress, dark)
}
