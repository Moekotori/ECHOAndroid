package app.echo.android.playback

import android.media.AudioRouting
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioTrackAudioOutput

/** Observe lifecycle/routing events only. PCM writes delegate without any extra work. */
@UnstableApi
internal class EchoRoutingAudioOutput(
    private val output: AudioTrackAudioOutput,
    private val routes: EchoOutputRouteMonitor,
) : AudioOutput by output {
    private val handler = Handler(Looper.getMainLooper())
    private val track = output.audioTrack
    @Volatile private var released = false
    @Volatile private var playing = false
    private val listener = AudioRouting.OnRoutingChangedListener { publish() }

    init { track.addOnRoutingChangedListener(listener, handler) }

    override fun play() {
        output.play()
        playing = true
        handler.post { publish() }
    }

    override fun pause() {
        playing = false
        output.pause()
        handler.post { routes.clearActualRoutes(this) }
    }

    override fun stop() {
        playing = false
        output.stop()
        handler.post { routes.clearActualRoutes(this) }
    }

    override fun flush() {
        playing = false
        output.flush()
        handler.post { routes.clearActualRoutes(this) }
    }

    override fun release() {
        released = true
        playing = false
        track.removeOnRoutingChangedListener(listener)
        handler.removeCallbacksAndMessages(null)
        handler.post { routes.clearActualRoutes(this) }
        output.release()
    }

    private fun publish() {
        if (released || !playing) return
        val devices = if (Build.VERSION.SDK_INT >= 36) track.routedDevices else listOfNotNull(track.routedDevice)
        routes.setActualRoutes(this, devices)
    }
}
