package app.echo.android.design

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

private const val LocaleRestingScale = 1f
private const val LocaleFadedScale = 0.985f

fun Modifier.echoLocaleSwitchLayer(
    progress: Animatable<Float, AnimationVector1D>,
    lightweight: Boolean,
    active: Boolean,
): Modifier = if (!active) {
    this
} else {
    graphicsLayer {
        val value = progress.value
        alpha = value
        if (!lightweight) {
            val scale = LocaleFadedScale + (LocaleRestingScale - LocaleFadedScale) * value
            scaleX = scale
            scaleY = scale
        }
    }
}

suspend fun Animatable<Float, AnimationVector1D>.runEchoLocaleSwitch(
    lightweight: Boolean,
    apply: suspend () -> Unit,
) {
    if (!ValueAnimator.areAnimatorsEnabled()) {
        apply()
        snapTo(1f)
        return
    }
    animateTo(0f, EchoMotion.localeFadeOut(lightweight))
    apply()
    withFrameNanos { }
    withFrameNanos { }
    animateTo(1f, EchoMotion.localeFadeIn(lightweight))
}
