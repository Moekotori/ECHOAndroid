package app.echo.android.playback

import android.content.Context
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

@UnstableApi
internal class EchoRenderersFactory(
    context: Context,
    private val equalizerProcessor: AudioProcessor,
) : DefaultRenderersFactory(context) {
    init {
        // Keep platform decoding first; use the bundled software renderer for unsupported formats.
        // This is format selection, not automatic recovery from a mid-track decoder failure.
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
        setEnableDecoderFallback(true)
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink =
        DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(equalizerProcessor))
            .setAudioOutputProvider(EchoAudioOutputProvider(context))
            .build()
}
