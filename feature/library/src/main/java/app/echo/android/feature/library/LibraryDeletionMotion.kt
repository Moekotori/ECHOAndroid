package app.echo.android.feature.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode

internal fun libraryEmptyTransition(lightweight: Boolean): ContentTransform = ContentTransform(
    targetContentEnter = if (lightweight) EnterTransition.None else fadeIn(tween(240, easing = EchoMotion.Silk)),
    initialContentExit = if (lightweight) ExitTransition.None else fadeOut(tween(240, easing = EchoMotion.SilkExit)),
    sizeTransform = null,
)

/** Lazy layout retains departing items and closes their gaps without delaying the database write. */
@Composable
internal fun LazyItemScope.libraryTrackItemMotion(): Modifier =
    if (LocalEchoEffectivePerformanceMode.current.isLightweight) Modifier
    else Modifier.animateItem(
        fadeInSpec = null,
        placementSpec = EchoMotion.silkOffset(320),
        fadeOutSpec = tween(durationMillis = 240, easing = EchoMotion.SilkExit),
    )

/** Pending rows stay readable; cancellation and failures smoothly restore the same layer. */
@Composable
internal fun Modifier.libraryDeletionFeedback(deleting: Boolean): Modifier {
    if (LocalEchoEffectivePerformanceMode.current.isLightweight) return this
    val progress = animateFloatAsState(
        if (deleting) 1f else 0f,
        animationSpec = EchoMotion.silkFloat(220),
        label = "library-delete-feedback",
    )
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
    return graphicsLayer {
        alpha = 1f - 0.24f * progress.value
        scaleX = 1f - 0.015f * progress.value
        scaleY = scaleX
        translationX = 6f * density * direction * progress.value
    }
}
