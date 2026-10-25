package app.echo.android.feature.player

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.echo.android.model.playback.EchoSleepTimerCancellationReason
import kotlinx.coroutines.flow.Flow

/** Foreground-only transient feedback; no notification or stale message when returning to the app. */
@Composable
fun SleepTimerFeedbackHost(events: Flow<EchoSleepTimerCancellationReason>) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(events, lifecycle, context) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var toast: Toast? = null
            try {
                events.collect { reason ->
                    val message = when (reason) {
                        EchoSleepTimerCancellationReason.PlaybackChanged -> R.string.sleep_cancel_playback
                        EchoSleepTimerCancellationReason.TrackChanged -> R.string.sleep_cancel_track
                        EchoSleepTimerCancellationReason.Seeked -> R.string.sleep_cancel_seek
                        EchoSleepTimerCancellationReason.QueueChanged -> R.string.sleep_cancel_queue
                        EchoSleepTimerCancellationReason.AudioFocusLost -> R.string.sleep_cancel_focus
                        EchoSleepTimerCancellationReason.OutputChanged -> R.string.sleep_cancel_output
                        EchoSleepTimerCancellationReason.PlaybackError -> R.string.sleep_cancel_error
                    }
                    toast?.cancel()
                    toast = Toast.makeText(context, message, Toast.LENGTH_SHORT).also { it.show() }
                }
            } finally { toast?.cancel() }
        }
    }
}
