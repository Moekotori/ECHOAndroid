package app.echo.android

import android.content.Context
import android.view.KeyEvent
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import app.echo.android.data.EchoSettingsStore
import app.echo.android.playback.EchoPlaybackProcessRuntime
import app.echo.android.playback.usbKeyVolumeStep
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

/** Application-owned: setting writes survive an Activity recreation and retain no View. */
@UnstableApi
internal class UsbVolumeKeys(context: Context) {
    private val context = context.applicationContext
    private val pending = Channel<Int>(Channel.CONFLATED)
    private val pressed = HashSet<Int>(2)
    private var toast: Toast? = null
    private var lastNoticeMs = -1_000L
    init {
        val store = EchoSettingsStore(this.context)
        EchoPlaybackProcessRuntime.scope.launch {
            for (first in pending) {
                var latest = first
                // Persist after the key repeat stream settles, so stale settings emissions
                // cannot pull the live volume backward during a long press.
                while (true) {
                    latest = withTimeoutOrNull(200) { pending.receive() } ?: break
                }
                try { store.setUsbVolumeStep(latest) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (error: Exception) {
                    app.echo.android.model.error.EchoErrorLog.record(
                        app.echo.android.model.error.EchoErrorSource.Playback, "USB volume could not be saved", throwable = error)
                }
            }
        }
    }
    fun down(keyCode: Int, repeat: Int): Boolean {
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> 1
            KeyEvent.KEYCODE_VOLUME_DOWN -> -1
            else -> return false
        }
        if (!EchoPlaybackProcessRuntime.usbExclusiveEnabled || EchoPlaybackProcessRuntime.usbExclusiveSinkStatus == null) return false
        pressed.add(keyCode)
        if (EchoPlaybackProcessRuntime.usbBitPerfectEnabled) {
            if (repeat == 0) notice(context.getString(R.string.usb_key_volume_locked))
            return true
        }
        val current = EchoPlaybackProcessRuntime.usbOutputOptions
        val next = usbKeyVolumeStep(current.effectiveVolumeStep, direction, repeat)
        EchoPlaybackProcessRuntime.setUsbOutputOptions(current.withVolumeStep(next))
        pending.trySend(next)
        val now = android.os.SystemClock.elapsedRealtime()
        if (repeat == 0 || now - lastNoticeMs >= 150) {
            lastNoticeMs = now
            notice(if (next == 0) context.getString(app.echo.android.feature.settings.R.string.usb_fine_muted)
                else context.getString(app.echo.android.feature.settings.R.string.usb_fine_value, app.echo.android.model.playback.EchoUsbFineVolume.db(next)))
        }
        return true
    }
    fun up(keyCode: Int) = pressed.remove(keyCode)
    private fun notice(text: String) {
        toast?.cancel()
        toast = Toast.makeText(context, text, Toast.LENGTH_SHORT).also { it.show() }
    }
}
