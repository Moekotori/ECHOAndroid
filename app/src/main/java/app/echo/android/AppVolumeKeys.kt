package app.echo.android

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.media3.common.util.UnstableApi
import app.echo.android.playback.EchoPlaybackProcessRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal sealed interface AppVolumeKeyFeedback {
    data class Level(val db: Float, val muted: Boolean) : AppVolumeKeyFeedback
    data object Locked : AppVolumeKeyFeedback
}

/** Foreground USB-exclusive keys only. Normal playback uses Android media volume. */
@UnstableApi
internal class AppVolumeKeys(private val application: EchoApplication) {
    private val pressed = HashSet<Int>(2)
    private val handler = Handler(Looper.getMainLooper())
    private val currentFeedback = MutableStateFlow<AppVolumeKeyFeedback?>(null)
    private val hideFeedback = Runnable { currentFeedback.value = null }
    val feedback = currentFeedback.asStateFlow()
    private var lastNoticeMs = -1_000L

    fun down(keyCode: Int, repeat: Int): Boolean {
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> 1
            KeyEvent.KEYCODE_VOLUME_DOWN -> -1
            else -> return false
        }
        val remote = application.echoLinkSession
        if (remote.castActive.value || remote.playbackRouter.remoteMode.value) return false
        val usb = EchoPlaybackProcessRuntime.usbAudioMonitor(application).status.value
        if (!usb.connected || !usb.exclusiveEnabled) return false
        if (EchoPlaybackProcessRuntime.usbVolumeLocked) {
            if (repeat == 0) showFeedback(AppVolumeKeyFeedback.Locked, durationMs = 2_600L)
        } else {
            if (!application.appVolumeController.adjust(direction)) return false
            val now = SystemClock.elapsedRealtime()
            if (repeat == 0 || now - lastNoticeMs >= 150) {
                lastNoticeMs = now
                val volume = application.appVolumeController.state.value
                showFeedback(AppVolumeKeyFeedback.Level(volume.levelDb, volume.muted), durationMs = 1_800L)
            }
        }
        pressed.add(keyCode)
        return true
    }

    fun up(keyCode: Int): Boolean = pressed.remove(keyCode)

    fun clear() {
        pressed.clear()
        handler.removeCallbacks(hideFeedback)
        currentFeedback.value = null
    }

    private fun showFeedback(feedback: AppVolumeKeyFeedback, durationMs: Long) {
        currentFeedback.value = feedback
        handler.removeCallbacks(hideFeedback)
        handler.postDelayed(hideFeedback, durationMs)
    }
}
