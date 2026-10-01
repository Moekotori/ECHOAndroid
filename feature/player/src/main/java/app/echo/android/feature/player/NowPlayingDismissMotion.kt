package app.echo.android.feature.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import app.echo.android.design.EchoMotion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import app.echo.android.design.echoHorizontalGestureOwnsScroll
import kotlinx.coroutines.Job

internal class NowPlayingDismissDragState {
    var offsetPx by mutableFloatStateOf(0f)
    var crossedThreshold by mutableStateOf(false)
    var settleJob: Job? = null
    var settleVelocity = 0f
    var isDragging = false
        private set

    fun beginDrag() {
        settleJob?.cancel()
        settleJob = null
        settleVelocity = 0f
        isDragging = true
    }

    fun finishDrag() { isDragging = false }

    fun applyDelta(delta: Float, thresholdPx: Float, onCrossedThreshold: (Boolean) -> Unit) {
        // A new finger movement owns the position immediately; an old spring cannot write it.
        beginDrag()
        val resisted = if (offsetPx > thresholdPx && delta > 0f) delta * 0.38f else delta
        offsetPx = (offsetPx + resisted).coerceAtLeast(0f)
        val crossed = offsetPx >= thresholdPx
        if (crossed != crossedThreshold) {
            crossedThreshold = crossed
            onCrossedThreshold(crossed)
        }
    }

    fun reset() {
        offsetPx = 0f
        crossedThreshold = false
        settleVelocity = 0f
        isDragging = false
    }
}

internal class NowPlayingDismissConnection(
    private val dragState: NowPlayingDismissDragState,
    private val enabled: () -> Boolean,
    private val threshold: () -> Float,
    private val onCrossedThreshold: (Boolean) -> Unit,
    private val onSettle: (Float) -> Unit,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || !enabled() || available.y == 0f || dragState.offsetPx <= 0f) {
            return Offset.Zero
        }
        if (!dragState.isDragging && echoHorizontalGestureOwnsScroll(Offset.Zero, available)) return Offset.Zero
        val consumed = if (available.y < 0f) available.y.coerceAtLeast(-dragState.offsetPx) else available.y
        dragState.applyDelta(consumed, threshold(), onCrossedThreshold)
        return Offset(0f, consumed)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // A child's fling/lyrics auto-scroll must never pull down the whole player.
        if (source != NestedScrollSource.UserInput || !enabled() || available.y <= 0f) return Offset.Zero
        if (!dragState.isDragging && echoHorizontalGestureOwnsScroll(consumed, available)) return Offset.Zero
        dragState.applyDelta(available.y, threshold(), onCrossedThreshold)
        return Offset(0f, available.y)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        dragState.finishDrag()
        if (!enabled() || dragState.offsetPx <= 0f) return Velocity.Zero
        onSettle(available.y)
        return Velocity(0f, available.y)
    }
}

@Composable
internal fun rememberNowPlayingDismissConnection(
    dragState: NowPlayingDismissDragState,
    enabled: State<Boolean>,
    thresholdPx: Float,
    onCrossedThreshold: (Boolean) -> Unit,
    onSettle: (Float) -> Unit,
): NestedScrollConnection {
    val thresholdState = rememberUpdatedState(thresholdPx)
    val crossedState = rememberUpdatedState(onCrossedThreshold)
    val settleState = rememberUpdatedState(onSettle)
    return remember(dragState, enabled) {
        NowPlayingDismissConnection(dragState, { enabled.value }, { thresholdState.value },
            { crossedState.value(it) }, { settleState.value(it) })
    }
}

internal suspend fun restoreNowPlayingDismiss(
    dragState: NowPlayingDismissDragState,
    velocityY: Float? = null,
    lightweight: Boolean = false,
) {
    dragState.finishDrag()
    if (dragState.offsetPx <= 0f) {
        dragState.reset()
        return
    }
    animate(
        initialValue = dragState.offsetPx,
        targetValue = 0f,
        initialVelocity = velocityY ?: dragState.settleVelocity,
        animationSpec = if (lightweight) tween(EchoMotion.PageLightweightMs) else spring<Float>(Spring.DampingRatioNoBouncy, stiffness = 420f),
    ) { value, velocity ->
        dragState.offsetPx = value.coerceAtLeast(0f)
        dragState.settleVelocity = velocity
    }
    dragState.reset()
}

internal suspend fun settleNowPlayingDismiss(
    dragState: NowPlayingDismissDragState,
    velocityY: Float,
    thresholdPx: Float,
    flingVelocityPx: Float,
    lightweight: Boolean = false,
    onDismiss: () -> Unit,
) {
    dragState.finishDrag()
    val shouldDismiss = dragState.offsetPx >= thresholdPx ||
        (velocityY >= flingVelocityPx && dragState.offsetPx > thresholdPx * 0.28f)
    if (shouldDismiss) {
        // Hold the gesture position while the host carries the page off screen.
        // Rewinding here would fight the downward exit animation.
        onDismiss()
    } else {
        restoreNowPlayingDismiss(dragState, velocityY, lightweight)
    }
}
