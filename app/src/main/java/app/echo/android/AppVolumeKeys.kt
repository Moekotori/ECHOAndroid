package app.echo.android

import android.view.KeyEvent
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import app.echo.android.playback.EchoPlaybackProcessRuntime
import app.echo.android.feature.settings.R as SettingsR

/** Foreground keys only. Android continues to own volume keys outside the Activity. */
@UnstableApi
internal class AppVolumeKeys(private val application: EchoApplication) {
    private val pressed = HashSet<Int>(2)
    private var toast: Toast? = null
    private var lastNoticeMs = -1_000L

    fun down(keyCode: Int, repeat: Int): Boolean {
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> 1
            KeyEvent.KEYCODE_VOLUME_DOWN -> -1
            else -> return false
        }
        val remote = application.echoLinkSession
        if (remote.castActive.value || remote.playbackRouter.remoteMode.value) return false
        if (EchoPlaybackProcessRuntime.usbBitPerfectEnabled) {
            if (repeat == 0) notice(application.getString(R.string.usb_key_volume_locked))
        } else {
            if (!application.appVolumeController.adjust(direction)) return false
            val volume = application.appVolumeController.state.value
            val now = android.os.SystemClock.elapsedRealtime()
            if (repeat == 0 || now - lastNoticeMs >= 150) {
                lastNoticeMs = now
                notice(if (volume.muted) application.getString(SettingsR.string.volume_muted) else
                    application.getString(SettingsR.string.volume_key_value, volume.levelDb))
            }
        }
        pressed.add(keyCode)
        return true
    }

    fun up(keyCode: Int): Boolean = pressed.remove(keyCode)
    fun clearPressed() { pressed.clear() }

    private fun notice(text: String) {
        toast?.cancel()
        toast = Toast.makeText(application, text, Toast.LENGTH_SHORT).also { it.show() }
    }
}
