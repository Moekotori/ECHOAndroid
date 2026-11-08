package app.echo.android.ui.playback

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** The phone's expanded player owns the lock only while resumed. */
@Composable
internal fun EchoPlayerOrientationEffect(locked: Boolean, active: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
    }
    val phone = LocalConfiguration.current.smallestScreenWidthDp < 600
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(activity, lifecycle, locked, active, phone) {
        val owner = activity
        var previous: Int? = null
        fun restore() {
            previous?.let { old ->
                if (owner?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LOCKED) owner.requestedOrientation = old
            }
            previous = null
        }
        fun apply() {
            if (owner != null && locked && active && phone && previous == null) {
                previous = owner.requestedOrientation
                owner.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> apply()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_DESTROY -> restore()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) apply()
        onDispose { lifecycle.removeObserver(observer); restore() }
    }
}
