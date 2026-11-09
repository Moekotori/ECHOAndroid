package app.echo.android

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Hand-off between the system splash (owned by [installEchoExitTransition]) and the in-app
 * launch splash. The overlay is composed only once [started] and [lightweight] are known.
 */
internal class EchoLaunchSplashState {
    /** The system splash has begun fading out; the timeline may run. */
    var started by mutableStateOf(false)

    /** Null until the effective performance mode has been resolved. */
    var lightweight by mutableStateOf<Boolean?>(null)

    /** Optional user startup image drawn instead of the pastel wash. */
    var background by mutableStateOf<Bitmap?>(null)

    var finished by mutableStateOf(false)

    fun start(background: Bitmap?, lightweight: Boolean) {
        // A delayed platform callback must not revive an expired/backgrounded overlay.
        if (finished) return
        this.background = background
        this.lightweight = lightweight
        started = true
    }

    fun finish() {
        finished = true
        background = null
    }
}
