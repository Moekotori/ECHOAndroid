package app.echo.android.design

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/** Ignore a stale enabled callback, and commit navigation only if its owner is still active. */
suspend fun collectEchoBackGesture(
    progress: Flow<Float>,
    isActive: () -> Boolean,
    onProgress: (Float) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    var lostOwnership = !isActive()
    try {
        progress.collect {
            if (!isActive()) lostOwnership = true
            if (!lostOwnership) onProgress(it.coerceIn(0f, 1f))
        }
        if (!lostOwnership && isActive()) onDismiss()
    } catch (cancelled: CancellationException) {
        if (!lostOwnership && isActive()) onCancel()
        throw cancelled
    }
}

/** Per-overlay gesture progress: a stacked overlay must not inherit the one dragged above it. */
@Composable
fun rememberEchoOverlayBack(visible: Boolean, lightweight: Boolean): EchoBackProgress {
    val back = rememberEchoBackProgress(lightweight)
    LaunchedEffect(visible) { if (visible) back.restore() }
    return back
}

@Composable
fun EchoOverlayBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit = {},
    onCancel: () -> Unit = { onProgress(0f) },
    onDismiss: () -> Unit,
) {
    val active = rememberUpdatedState(enabled)
    val ownership = remember(enabled) { Any() }
    val currentOwnership = rememberUpdatedState(ownership)
    val update = rememberUpdatedState(onProgress)
    val cancel = rememberUpdatedState(onCancel)
    val dismiss = rememberUpdatedState(onDismiss)
    PredictiveBackHandler(enabled = enabled) { progress ->
        val gestureOwner = currentOwnership.value
        val gestureUpdate = update.value
        val gestureCancel = cancel.value
        val gestureDismiss = dismiss.value
        fun ownerActive() = active.value && currentOwnership.value === gestureOwner
        var lostOwnership = !ownerActive()
        try {
            progress.collect { event ->
                if (!ownerActive()) lostOwnership = true
                if (!lostOwnership) gestureUpdate(event.progress.coerceIn(0f, 1f))
            }
            if (!lostOwnership && ownerActive()) gestureDismiss()
        } catch (cancelled: CancellationException) {
            if (!lostOwnership && ownerActive()) gestureCancel()
            throw cancelled
        }
    }
}
