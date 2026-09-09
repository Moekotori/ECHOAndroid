package app.echo.android.playback

import android.content.Context
import android.os.Handler
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

@UnstableApi
internal class EchoRenderersFactory(
    context: Context,
    private val equalizerProcessor: AudioProcessor,
    private val smartTransitionProcessor: AudioProcessor = EchoSmartTransitionMixer(),
) : DefaultRenderersFactory(context) {
    init {
        // Keep platform decoding first; use the bundled software renderer for unsupported formats.
        // This is format selection, not automatic recovery from a mid-track decoder failure.
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
        setEnableDecoderFallback(true)
    }

    override fun buildAudioRenderers(context: Context, extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector, enableDecoderFallback: Boolean, audioSink: AudioSink,
        eventHandler: Handler, eventListener: AudioRendererEventListener, out: ArrayList<Renderer>) {
        out.add(EchoBitPerfectAudioRenderer(eventHandler, eventListener, EchoBitPerfectAudioSink(context)))
        val normal = ArrayList<Renderer>()
        super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback,
            audioSink, eventHandler, eventListener, normal)
        normal.forEach { out.add(EchoNormalAudioRenderer(it)) }
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink =
        DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(smartTransitionProcessor, equalizerProcessor))
            .setAudioOutputProvider(EchoAudioOutputProvider(context))
            .build()
}
