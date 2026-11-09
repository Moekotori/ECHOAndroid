package app.echo.android.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode

@Composable
internal fun <T> PlaybackModeContent(
    value: T,
    animate: Boolean,
    content: @Composable (T) -> Unit,
) {
    if (!animate || LocalEchoEffectivePerformanceMode.current.isLightweight) {
        content(value)
        return
    }
    AnimatedContent(
        targetState = value,
        modifier = Modifier.clearAndSetSemantics {},
        transitionSpec = {
            (fadeIn(tween(170, easing = EchoMotion.Silk)) +
                scaleIn(EchoMotion.silkFloat(220), initialScale = 0.78f) +
                slideInVertically(EchoMotion.silkOffset(220)) { it / 4 }) togetherWith
                (fadeOut(tween(100, easing = EchoMotion.SilkExit)) +
                    slideOutVertically(EchoMotion.silkOffset(150)) { -it / 4 })
        },
        contentAlignment = Alignment.Center,
        label = "playback-mode",
        content = { content(it) },
    )
}

@Composable
internal fun PlaybackModeIcon(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier,
    animate: Boolean,
) {
    if (!animate) {
        EchoIcon(icon, label, modifier, tint)
        return
    }
    Box(modifier.clearAndSetSemantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        PlaybackModeContent(icon, animate = true) {
            EchoIcon(it, null, Modifier.fillMaxSize(), tint)
        }
    }
}
