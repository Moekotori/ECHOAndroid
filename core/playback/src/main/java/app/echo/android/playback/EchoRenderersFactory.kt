package app.echo.android.playback

import android.content.Context
import android.os.Handler
import androidx.media3.common.Format
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

@UnstableApi
internal class EchoRenderersFactory(
    context: Context,
    private val equalizerProcessor: AudioProcessor,
    private val smartTransitionProcessor: AudioProcessor = EchoSmartTransitionMixer(),
    private val channelBalanceProcessor: AudioProcessor = EchoChannelBalanceAudioProcessor(),
    private val audioPipelineState: EchoAudioPipelineState = EchoAudioPipelineState(),
    private val headphoneCorrectionProcessor: AudioProcessor = EchoEqualizerAudioProcessor(),
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
        val runtime = EchoPlaybackProcessRuntime
        val usb = runtime.usbAudioMonitor(context)
        val selectStrict: (Format) -> Boolean = { format ->
            runtime.usbBitPerfectEnabled && format.canUseStrictUsb(usb.playbackSnapshot, runtime.usbOutputOptions.dopDeviceLimits)
        }
        val strict = EchoBitPerfectAudioSink(context)
        out.add(EchoBitPerfectAudioRenderer(eventHandler, eventListener, strict, EchoCuePcmAudioSink(strict), selectStrict))
        out.add(EchoCueAudioRenderer(eventHandler, eventListener, audioSink, selectStrict))
        val normal = ArrayList<Renderer>()
        super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector.withAlacWorkaround(), enableDecoderFallback,
            audioSink, eventHandler, eventListener, normal)
        normal.forEach { out.add(EchoNormalAudioRenderer(it, selectStrict)) }
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink {
        val processor = EchoDspAudioProcessor(arrayOf(smartTransitionProcessor, equalizerProcessor, headphoneCorrectionProcessor, channelBalanceProcessor))
        val sink = DefaultAudioSink.Builder(context)
            // Custom DSP uses internal float buffers and preserves the negotiated output encoding.
            .setEnableFloatOutput(false)
            .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(EchoPcm8AudioProcessor(), processor, EchoUsbFormatAudioProcessor(context, audioPipelineState)))
            .setAudioOutputProvider(EchoAudioOutputProvider(context))
            .build()
        return EchoCuePcmAudioSink(EchoTransitionClockAudioSink(sink, processor))
    }
}
