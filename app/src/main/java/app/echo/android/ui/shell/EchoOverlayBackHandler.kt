package app.echo.android.ui.shell

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Ignore a stale enabled callback, and commit navigation only if its owner is still active. */
internal suspend fun collectEchoBackGesture(
    progress: Flow<Float>,
    isActive: () -> Boolean,
    onProgress: (Float) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isActive()) return
    var lostOwnership = false
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

@Composable
internal fun EchoOverlayBackHandler(
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
        collectEchoBackGesture(progress.map { it.progress },
            { active.value && currentOwnership.value === gestureOwner },
            gestureUpdate, gestureCancel, gestureDismiss)
    }
}
