package app.echo.android.playback

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.widget.Toast
import app.echo.android.model.error.EchoDiagnosticEvents
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource

/** Low frequency control feedback, owned by the service; no audio callback work. */
internal class EchoAudioFocusFeedback(private val context: Context) {
    private var toast: Toast? = null
    private var lastResult: Int? = null
    private var lastNoticeMs = Long.MIN_VALUE

    fun clear() { toast?.cancel(); toast = null; lastResult = null }

    fun onRequest(result: Int) {
        EchoDiagnosticEvents.record(EchoDiagnosticEvents.Event.AudioFocus, result.toLong(), 1)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            toast?.cancel()
            lastResult = null
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (lastResult == result && now - lastNoticeMs < 3_000) return
        lastResult = result
        lastNoticeMs = now
        val waiting = result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED
        if (!waiting) EchoErrorLog.record(EchoErrorSource.Playback, "System denied audio focus", "request_result=$result")
        toast?.cancel()
        toast = Toast.makeText(context, if (waiting) R.string.audio_focus_waiting else R.string.audio_focus_denied, Toast.LENGTH_SHORT)
            .also { it.show() }
    }
}
