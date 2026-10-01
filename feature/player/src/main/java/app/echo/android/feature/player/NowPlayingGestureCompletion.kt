package app.echo.android.feature.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

/** Nested scroll reports zero-velocity flings on cancellation too. Keep the real outcome. */
internal class NowPlayingGestureCompletion {
    private var pending: CompletableDeferred<Boolean>? = null
    val result: Deferred<Boolean>? get() = pending
    fun begin() {
        pending?.complete(false)
        pending = CompletableDeferred()
    }
    fun finish(released: Boolean) { pending?.complete(released) }
}

internal fun Modifier.observeNowPlayingGesture(completion: NowPlayingGestureCompletion): Modifier =
    pointerInput(completion) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            completion.begin()
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    if (event.changes.count { it.pressed } > 1) break
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!pointer.pressed) {
                        completion.finish(pointer.changedToUpIgnoreConsumed())
                        break
                    }
                }
            } finally { completion.finish(false) }
        }
    }
