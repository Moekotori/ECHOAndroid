package app.echo.android.feature.connect

import androidx.compose.foundation.gestures.Orientation
import app.echo.android.design.echoDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** One command per completed gesture; progress updates do not restart the detector. */
@Composable
internal fun Modifier.remoteTrackSwipe(
    enabled: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    trackKey: String? = null,
): Modifier {
    var offset by remember { mutableFloatStateOf(0f) }
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    val minimumFlingDistance = with(LocalDensity.current) { 12.dp.toPx() }
    val flingThreshold = with(LocalDensity.current) { 780.dp.toPx() }
    LaunchedEffect(enabled, trackKey) { offset = 0f }
    return clipToBounds()
        .echoDrag(
            orientation = Orientation.Horizontal,
            enabled = enabled,
            gestureKey = trackKey,
            onDelta = { delta ->
                offset = (offset + delta).coerceIn(-threshold * 2, threshold * 2)
            },
            onStart = { offset = 0f },
            onStop = { velocity ->
                val distance = offset
                offset = 0f
                val fling = abs(velocity) >= flingThreshold &&
                    abs(distance) >= minimumFlingDistance && velocity * distance > 0f
                if (abs(distance) >= threshold || fling) {
                    if (distance < 0f) next() else previous()
                }
            },
            onCancel = { offset = 0f },
        )
        .graphicsLayer { translationX = offset }
}
