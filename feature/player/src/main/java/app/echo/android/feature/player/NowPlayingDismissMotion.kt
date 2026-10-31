package app.echo.android.feature.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import app.echo.android.design.EchoMotion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import app.echo.android.design.echoHorizontalGestureOwnsScroll
import kotlin.math.abs
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
        offsetPx = nowPlayingPullOffset(offsetPx, delta, thresholdPx)
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
    private var settledInPreFling = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || !enabled() || available.y == 0f || dragState.offsetPx <= 0f) {
            return Offset.Zero
        }
        if (!dragState.isDragging && echoHorizontalGestureOwnsScroll(Offset.Zero, available)) return Offset.Zero
        val thresholdPx = threshold()
        val distanceToRest = nowPlayingPullDistance(dragState.offsetPx, thresholdPx)
        val consumed = if (available.y < 0f) available.y.coerceAtLeast(-distanceToRest) else available.y
        dragState.applyDelta(consumed, thresholdPx, onCrossedThreshold)
        return Offset(0f, consumed)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // A child's fling/lyrics auto-scroll must never pull down the whole player.
        if (source != NestedScrollSource.UserInput || !enabled() || available.y <= 0f) return Offset.Zero
        if (!dragState.isDragging && echoHorizontalGestureOwnsScroll(consumed, available)) return Offset.Zero
        dragState.applyDelta(available.y, threshold(), onCrossedThreshold)
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        settledInPreFling = false
        if (!enabled() || !dragState.isDragging || dragState.offsetPx <= 0f) return Velocity.Zero
        // The displaced player owns this vertical pull. Settle before the child list can consume
        // the release velocity, otherwise a fast upward reversal is mistaken for a slow release.
        dragState.finishDrag()
        settledInPreFling = true
        onSettle(available.y)
        return Velocity(0f, available.y)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        dragState.finishDrag()
        if (settledInPreFling) { settledInPreFling = false; return Velocity.Zero }
        if (!enabled() || dragState.offsetPx <= 0f) return Velocity.Zero
        onSettle(available.y)
        return Velocity(0f, available.y)
    }
}

private const val NowPlayingPullResistance = 0.38f

/** Keep resistance reversible and independent of the number of MOVE events. */
internal fun nowPlayingPullDistance(offsetPx: Float, thresholdPx: Float): Float =
    if (offsetPx > thresholdPx) thresholdPx + (offsetPx - thresholdPx) / NowPlayingPullResistance
    else offsetPx

internal fun nowPlayingPullOffset(offsetPx: Float, deltaPx: Float, thresholdPx: Float): Float {
    val distance = (nowPlayingPullDistance(offsetPx, thresholdPx) + deltaPx).coerceAtLeast(0f)
    return if (distance > thresholdPx) thresholdPx + (distance - thresholdPx) * NowPlayingPullResistance
        else distance
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

/** Top corners open over a short travel, then stay rounded for the rest of the slide. */
internal fun nowPlayingSheetCornerFraction(offsetPx: Float, travelPx: Float): Float {
    if (offsetPx <= 0f || travelPx <= 0f) return 0f
    val t = (offsetPx / travelPx).coerceIn(0f, 1f)
    return 1f - (1f - t) * (1f - t)
}

/** Zero means the host has not been measured yet, so the finger position is the exit. */
internal fun nowPlayingDismissExitTarget(offsetPx: Float, exitDistancePx: Float): Float =
    exitDistancePx.coerceAtLeast(offsetPx)

internal fun nowPlayingShouldDismiss(
    offsetPx: Float,
    velocityY: Float,
    thresholdPx: Float,
    flingVelocityPx: Float,
    forceDismiss: Boolean = false,
): Boolean = forceDismiss || (velocityY > -flingVelocityPx &&
    (offsetPx >= thresholdPx || (velocityY >= flingVelocityPx && offsetPx > thresholdPx * 0.28f)))

internal suspend fun settleNowPlayingDismiss(
    dragState: NowPlayingDismissDragState,
    velocityY: Float,
    thresholdPx: Float,
    flingVelocityPx: Float,
    lightweight: Boolean = false,
    exitDistancePx: Float = 0f,
    forceDismiss: Boolean = false,
    onSuppressArtworkFlight: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    dragState.finishDrag()
    val shouldDismiss = nowPlayingShouldDismiss(
        dragState.offsetPx, velocityY, thresholdPx, flingVelocityPx, forceDismiss,
    )
    if (shouldDismiss) {
        // Drop the shared-element pair before the window leaves, or the cover restarts from center.
        onSuppressArtworkFlight()
        val target = nowPlayingDismissExitTarget(dragState.offsetPx, exitDistancePx)
        // Carry the release velocity off screen. A zero distance still hands off at the finger.
        if (target - dragState.offsetPx > 0.5f) {
            val launchVelocity = if (forceDismiss) velocityY.coerceAtLeast(target * 3.2f) else velocityY.coerceAtLeast(0f)
            animate(
                initialValue = dragState.offsetPx,
                targetValue = target,
                initialVelocity = launchVelocity,
                animationSpec = if (lightweight) tween(EchoMotion.PageLightweightMs) else EchoMotion.silkFloat(EchoMotion.OverlayExitMs),
            ) { value, velocity ->
                dragState.offsetPx = value.coerceAtLeast(0f)
                dragState.settleVelocity = velocity
            }
        }
        onDismiss()
    } else {
        restoreNowPlayingDismiss(dragState, velocityY, lightweight)
    }
}

/**
 * Downward drags on non-scrolling surface dismiss the player.
 * A child that consumes movement (pager, lyrics, header) keeps it; a button's DOWN alone does not.
 */
@Composable
internal fun Modifier.nowPlayingSurfaceDismiss(
    enabled: () -> Boolean,
    onDelta: (Float) -> Unit,
    onStop: (Float) -> Unit,
    onCancel: () -> Unit,
): Modifier {
    val enabledState = rememberUpdatedState(enabled)
    val deltaState = rememberUpdatedState(onDelta)
    val stopState = rememberUpdatedState(onStop)
    val cancelState = rememberUpdatedState(onCancel)
    return pointerInput(Unit) {
        val tracker = VelocityTracker()
        var tracking = false
        try {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (!enabledState.value() || currentEvent.changes.size != 1) return@awaitEachGesture
                var dragging = false
                var accumulated = Offset.Zero
                tracker.resetTracking()
                tracker.addPosition(down.uptimeMillis, down.position)
                val pointerId = down.id
                while (true) {
                    val event = awaitPointerEvent(if (dragging) PointerEventPass.Initial else PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == pointerId }
                    val singlePointer = event.changes.size == 1
                    val consumed = change?.isConsumed == true && change.previousPressed
                    if (change == null || !change.pressed || !singlePointer || consumed || !enabledState.value()) {
                        if (dragging) {
                            dragging = false
                            tracking = false
                            if (change != null && !change.pressed && change.previousPressed && singlePointer && !consumed && enabledState.value()) {
                                val releaseDelta = change.position.y - change.previousPosition.y
                                if (releaseDelta != 0f) deltaState.value(releaseDelta)
                                tracker.addPosition(change.uptimeMillis, change.position)
                                change.consume()
                                stopState.value(tracker.calculateVelocity().y)
                            } else {
                                cancelState.value()
                            }
                        }
                        break
                    }
                    val delta = change.position - change.previousPosition
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (!dragging) {
                        accumulated += delta
                        val slop = viewConfiguration.touchSlop
                        val downward = accumulated.y > slop && accumulated.y > abs(accumulated.x) * 1.15f
                        if (accumulated.getDistance() < slop) continue
                        if (!downward) break
                        dragging = true
                        tracking = true
                        deltaState.value(accumulated.y - slop)
                        change.consume()
                    } else {
                        deltaState.value(delta.y)
                        change.consume()
                    }
                }
            }
        } finally {
            if (tracking) {
                tracking = false
                cancelState.value()
            }
        }
    }
}
