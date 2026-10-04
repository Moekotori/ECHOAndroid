package app.echo.android.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode

internal fun librarySelectionEnter(lightweight: Boolean): EnterTransition =
    if (lightweight) EnterTransition.None else fadeIn(tween(EchoMotion.FadeMs)) +
        expandHorizontally(EchoMotion.silkSize(EchoMotion.CollapseMs), expandFrom = Alignment.Start)

internal fun librarySelectionExit(lightweight: Boolean): ExitTransition =
    if (lightweight) ExitTransition.None else fadeOut(tween(EchoMotion.FadeExitMs)) +
        shrinkHorizontally(EchoMotion.silkSize(EchoMotion.CollapseMs), shrinkTowards = Alignment.Start)

/** Animation state is read while drawing; selection feedback does not rebuild row content every frame. */
@Composable
internal fun rememberLibrarySelectionProgress(selected: Boolean): State<Float> {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    return animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (lightweight) snap() else EchoMotion.silkFloat(EchoMotion.FeedbackMs),
        label = "library-selection",
    )
}

internal fun Modifier.librarySelectionBackground(progress: State<Float>, color: Color): Modifier = drawBehind {
    if (progress.value > 0f) {
        drawRoundRect(color.copy(alpha = 0.065f * progress.value), cornerRadius = CornerRadius(10.dp.toPx()))
    }
}

@Composable
internal fun LibraryTrackSelection(
    active: Boolean,
    selected: Boolean,
    enabled: Boolean,
    progress: State<Float>,
    label: String,
    onToggle: () -> Unit,
) {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val scheme = MaterialTheme.colorScheme
    AnimatedVisibility(active, enter = librarySelectionEnter(lightweight), exit = librarySelectionExit(lightweight)) {
        Box(
            Modifier.size(width = 48.dp, height = 52.dp)
                .semantics { contentDescription = label }
                .toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() }),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(22.dp)) {
                val radius = size.minDimension / 2 - 1.dp.toPx()
                val alpha = if (enabled) 1f else 0.38f
                drawCircle(scheme.outline.copy(alpha = alpha * (1f - progress.value)), radius, style = Stroke(1.5.dp.toPx()))
                drawCircle(scheme.onSurface.copy(alpha = alpha * progress.value), radius)
            }
            EchoIcon(
                Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(15.dp).graphicsLayer {
                    alpha = progress.value * if (enabled) 1f else 0.38f
                    scaleX = 0.8f + 0.2f * progress.value
                    scaleY = scaleX
                },
                tint = scheme.surface,
            )
        }
    }
}
