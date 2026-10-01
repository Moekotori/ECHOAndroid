package app.echo.android.feature.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode

@Composable
internal fun Modifier.settingsEntrance(isActive: Boolean): Modifier {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val reveal = remember { Animatable(if (isActive && !lightweight) 0f else 1f) }
    LaunchedEffect(isActive, lightweight) {
        // Keep the leaving page visible; the pager owns its horizontal exit.
        if (!isActive || lightweight) {
            reveal.snapTo(1f)
        } else {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(durationMillis = 280, easing = EchoMotion.Silk))
        }
    }
    return graphicsLayer {
        alpha = 0.35f + 0.65f * reveal.value
        translationY = 12.dp.toPx() * (1f - reveal.value)
    }
}
