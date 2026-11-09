package app.echo.android.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoPressFeedback

/** Saved inside each stable lazy-item key: scrolling back never replays its entrance. */
@Composable
internal fun HomeSectionEntrance(
    order: Int,
    animateEntrance: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val animate = animateEntrance && !LocalEchoEffectivePerformanceMode.current.isLightweight
    var shown by rememberSaveable { mutableStateOf(false) }
    val reveal = remember(animate) { Animatable(if (shown || !animate) 1f else 0f) }
    LaunchedEffect(reveal) {
        shown = true
        if (reveal.value < 1f) reveal.animateTo(1f, tween(
            EchoMotion.CardRevealMs,
            delayMillis = order.coerceIn(0, 3) * EchoMotion.CardStaggerMs,
            easing = EchoMotion.Silk,
        ))
    }
    Column(
        modifier = if (animate) Modifier.graphicsLayer {
            alpha = reveal.value
            translationY = 12.dp.toPx() * (1f - reveal.value)
        } else Modifier,
        content = content,
    )
}

internal fun Modifier.homeCardClickable(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    echoPressFeedback(source)
        .clickable(source, LocalIndication.current, role = Role.Button, onClick = onClick)
}
