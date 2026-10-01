package app.echo.android.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

internal object EchoPageRevealPolicy {
    const val MaxSections = 4
    const val DurationMs = 240
    const val StaggerMs = 32
    const val TimelineMs = DurationMs + (MaxSections - 1) * StaggerMs

    fun progress(timeline: Float, order: Int): Float {
        if (order !in 0 until MaxSections) return 1f
        val elapsed = timeline.coerceIn(0f, 1f) * TimelineMs - order * StaggerMs
        return EchoMotion.Silk.transform((elapsed / DurationMs).coerceIn(0f, 1f))
    }
}

internal class EchoPageRevealScope(val progress: State<Float>) {
    val viewport = mutableStateOf<LayoutCoordinates?>(null)
    private var nextOrder = 0
    fun reserveOrder(): Int = nextOrder++
}

internal val LocalEchoPageReveal = compositionLocalOf<EchoPageRevealScope?> { null }

/** A pager activation can reveal its sections without fading or moving the whole page twice. */
@Composable
fun EchoPageEntrance(active: Boolean, content: @Composable () -> Unit) {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val progress = remember { Animatable(if (active && !lightweight) 0f else 1f) }
    LaunchedEffect(active, lightweight) {
        if (!active || lightweight) progress.snapTo(1f)
        else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(EchoPageRevealPolicy.TimelineMs, easing = LinearEasing))
        }
    }
    EchoPageRevealProvider(progress.asState(), enabled = !lightweight, content = content)
}

@Composable
internal fun EchoPageRevealProvider(
    progress: State<Float>,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val parent = LocalEchoPageReveal.current
    val combined = remember(progress, parent) {
        derivedStateOf { minOf(progress.value, parent?.progress?.value ?: 1f) }
    }
    val scope = remember(combined, enabled) { if (enabled) EchoPageRevealScope(combined) else null }
    CompositionLocalProvider(LocalEchoPageReveal provides scope) {
        if (scope == null) content()
        else Box(
            modifier = Modifier.onGloballyPositioned { scope.viewport.value = it },
            propagateMinConstraints = true,
        ) { content() }
    }
}

/** Only the first four initially visible groups participate. Scrolling never starts a reveal. */
@Composable
fun Modifier.echoPageSection(order: Int? = null): Modifier {
    val scope = LocalEchoPageReveal.current ?: return this
    val slot = order ?: remember(scope) { scope.reserveOrder() }
    if (slot !in 0 until EchoPageRevealPolicy.MaxSections) return this
    val viewport = scope.viewport.value
    var initiallyVisible by remember(scope) { mutableStateOf<Boolean?>(null) }
    return onGloballyPositioned { coordinates ->
        if (initiallyVisible == null && viewport?.isAttached == true) {
            // Measure inside the page/sheet, before its entrance translation moves it offscreen.
            val top = viewport.localPositionOf(coordinates, Offset.Zero).y
            val height = viewport.size.height
            initiallyVisible = top < height && top + coordinates.size.height > 0f
        }
    }.graphicsLayer {
        val reveal = if (initiallyVisible != false) EchoPageRevealPolicy.progress(scope.progress.value, slot) else 1f
        alpha = reveal
        translationY = 12.dp.toPx() * (1f - reveal)
    }
}
