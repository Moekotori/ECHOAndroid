package app.echo.android.ui.shell

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
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
    try {
        progress.collect { if (isActive()) onProgress(it.coerceIn(0f, 1f)) }
        if (isActive()) onDismiss()
    } catch (cancelled: CancellationException) {
        if (isActive()) onCancel()
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
    val update = rememberUpdatedState(onProgress)
    val cancel = rememberUpdatedState(onCancel)
    val dismiss = rememberUpdatedState(onDismiss)
    PredictiveBackHandler(enabled = enabled) { progress ->
        collectEchoBackGesture(progress.map { it.progress }, { active.value },
            { update.value(it) }, { cancel.value() }, { dismiss.value() })
    }
}
