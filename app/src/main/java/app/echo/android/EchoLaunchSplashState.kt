package app.echo.android

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Hand-off between the system splash (owned by [installEchoExitTransition]) and the in-app
 * launch splash. The overlay stays a plain background until [started] and [lightweight] are known.
 */
internal class EchoLaunchSplashState {
    /** The system splash has begun fading out; the timeline may run. */
    var started by mutableStateOf(false)

    /** Null until the effective performance mode has been resolved. */
    var lightweight by mutableStateOf<Boolean?>(null)

    /** Optional user startup image drawn instead of the pastel wash. */
    var background by mutableStateOf<Bitmap?>(null)

    var finished by mutableStateOf(false)
}
