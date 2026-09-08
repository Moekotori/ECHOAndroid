package app.echo.android.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

/** Share the click source with Material controls; never intercept pointer input for feedback. */
fun Modifier.echoPressFeedback(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
): Modifier = composed {
    val pressed = interactionSource.collectIsPressedAsState()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val scale = animateFloatAsState(
        targetValue = if (enabled && pressed.value && !lightweight) EchoMotion.PressedScale else 1f,
        animationSpec = if (lightweight) snap() else EchoMotion.silkFloat(EchoMotion.FeedbackMs),
        label = "echo-press",
    )
    // Read in the layer: pressing a song must not recompose the entire row every frame.
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** Keeps ripple, keyboard activation, click semantics and scroll cancellation intact. */
fun Modifier.echoClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    echoPressFeedback(source, enabled).clickable(
        interactionSource = source,
        indication = LocalIndication.current,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick,
    )
}

fun Modifier.echoCombinedClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    echoPressFeedback(source, enabled).combinedClickable(
        interactionSource = source,
        indication = LocalIndication.current,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        onClick = onClick,
    )
}

/** Use on a container before fixed size modifiers. Do not nest with EchoExpand for the same change. */
fun Modifier.echoAnimateContentSize(): Modifier = composed {
    if (LocalEchoEffectivePerformanceMode.current.isLightweight) Modifier
    else Modifier.animateContentSize(EchoMotion.silkSize(EchoMotion.ExpandMs))
}

/** Collapsed content is disposed after exit, including its focus and accessibility nodes. */
@Composable
fun EchoExpand(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    AnimatedVisibility(
        visible = expanded,
        modifier = modifier,
        enter = fadeIn(tween(EchoMotion.FadeMs)) + expandVertically(
            animationSpec = if (lightweight) snap() else EchoMotion.silkSize(EchoMotion.ExpandMs),
        ),
        exit = fadeOut(tween(EchoMotion.FadeExitMs)) + shrinkVertically(
            animationSpec = if (lightweight) snap() else EchoMotion.silkSize(EchoMotion.CollapseMs),
        ),
    ) { content() }
}

/** For small state changes (icons / labels), never for continuously ticking playback position. */
@Composable
fun <T> EchoStateContent(
    state: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        transitionSpec = { EchoMotion.stateChange() },
        label = "echo-state",
    ) { content(it) }
}

/** Stable item keys are required. Paging loads do not replay an entrance animation. */
@Composable
fun androidx.compose.foundation.lazy.LazyItemScope.echoItemMotion(): Modifier =
    if (LocalEchoEffectivePerformanceMode.current.isLightweight) Modifier
    else Modifier.animateItem(
        fadeInSpec = null,
        placementSpec = EchoMotion.silkOffset(EchoMotion.ExpandMs),
        fadeOutSpec = tween(EchoMotion.FadeExitMs),
    )

/** Resolve preferences in composition, before entering AnimatedContent's transition lambda. */
@Composable
fun rememberEchoContentMotion(): EchoContentMotion {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    return remember(lightweight) { EchoContentMotion(lightweight) }
}

class EchoContentMotion internal constructor(private val lightweight: Boolean) {
    fun pagePush() = if (lightweight) EchoMotion.stateChange() else EchoMotion.pagePush()
    fun pagePop() = if (lightweight) EchoMotion.stateChange() else EchoMotion.pagePop()
    fun tabSwitch(forward: Boolean) =
        if (lightweight) EchoMotion.stateChange() else EchoMotion.tabSwitch(forward)
}

/** Apply to a downward chevron so direction changes are continuous when interrupted. */
fun Modifier.echoExpandIndicator(expanded: Boolean): Modifier = composed {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val rotation = animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (lightweight) snap() else EchoMotion.silkFloat(EchoMotion.CollapseMs),
        label = "echo-expand-indicator",
    )
    graphicsLayer { rotationZ = rotation.value }
}
