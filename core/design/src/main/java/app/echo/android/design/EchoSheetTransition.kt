package app.echo.android.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** One transition owns the scrim, sheet and content disposal; reversing it keeps the same surface. */
@Composable
fun EchoSheetOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onHidden: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val scrimOpacity = if (LocalEchoDarkTheme.current) 0.28f else 0.14f
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = visible
    val hidden = rememberUpdatedState(onHidden)
    LaunchedEffect(visibility.isIdle, visibility.currentState, visible) {
        if (visibility.isIdle && !visibility.currentState && !visible) hidden.value()
    }
    AnimatedVisibility(
        visibleState = visibility,
        enter = EnterTransition.None,
        exit = ExitTransition.None,
        modifier = modifier.fillMaxSize(),
    ) {
        val sheet = transition.animateFloat(
            transitionSpec = {
                if (lightweight) tween(EchoMotion.PageLightweightMs)
                else EchoMotion.silkFloat(if (targetState == EnterExitState.Visible) 420 else 300)
            },
            label = "echo-sheet-position",
        ) { if (it == EnterExitState.Visible) 1f else 0f }
        val scrim = transition.animateFloat(
            transitionSpec = {
                if (lightweight) tween(90)
                else EchoMotion.silkFloat(if (targetState == EnterExitState.Visible) 220 else 180)
            },
            label = "echo-sheet-scrim",
        ) { if (it == EnterExitState.Visible) 1f else 0f }
        val reveal = transition.animateFloat(
            transitionSpec = { tween(if (lightweight) 0 else EchoPageRevealPolicy.TimelineMs, easing = LinearEasing) },
            label = "echo-sheet-sections",
        ) { if (it == EnterExitState.PreEnter) 0f else 1f }
        EchoPageSurface(active = visible) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()
                    .graphicsLayer { alpha = scrim.value }
                    .background(Color.Black.copy(alpha = scrimOpacity))
                    .clickable(
                        enabled = visible,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ))
                Box(Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .widthIn(max = 720.dp).graphicsLayer {
                    val progress = sheet.value.coerceIn(0f, 1f)
                    translationY = if (lightweight) 0f else size.height * (1f - progress)
                    alpha = if (lightweight) progress else 1f
                }) {
                    EchoPageRevealProvider(reveal, enabled = !lightweight, content = content)
                }
            }
        }
    }
}

/** The sheet stays outside this modifier. Drag progress can restore the background continuously. */
@Composable
fun Modifier.echoSheetUnderlay(visible: Boolean, dragProgress: () -> Float = { 0f }): Modifier {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val depth = animateFloatAsState(
        targetValue = if (visible && !lightweight) 1f else 0f,
        animationSpec = if (lightweight) tween(0) else EchoMotion.silkFloat(420),
        label = "echo-sheet-underlay",
    )
    return graphicsLayer {
        val progress = depth.value.coerceIn(0f, 1f) * (1f - dragProgress().coerceIn(0f, 1f))
        transformOrigin = TransformOrigin(0.5f, 0.08f)
        scaleX = 1f - 0.035f * progress
        scaleY = scaleX
        translationY = 8.dp.toPx() * progress
        shape = RoundedCornerShape((20f * progress).dp)
        clip = progress > 0.001f
    }
}
