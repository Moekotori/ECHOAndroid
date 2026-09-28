package app.echo.android.listening

import app.echo.android.model.listening.EchoListeningAudio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal interface EchoListeningAudioSink {
    val phase: StateFlow<EchoListeningAudio>
    fun startEpoch(epoch: Long)
    fun offer(packet: EchoListeningPacket)
    fun setVolume(volume: Float)
    fun stop()
}

internal interface EchoListeningNotifier {
    fun show(title: String, artist: String)
    fun hide()
}

internal object EchoListeningNoNotifier : EchoListeningNotifier {
    override fun show(title: String, artist: String) = Unit
    override fun hide() = Unit
}

internal class EchoListeningIdleSink : EchoListeningAudioSink {
    override val phase = MutableStateFlow(EchoListeningAudio.Off)
    override fun startEpoch(epoch: Long) = Unit
    override fun offer(packet: EchoListeningPacket) = Unit
    override fun setVolume(volume: Float) = Unit
    override fun stop() = Unit
}
