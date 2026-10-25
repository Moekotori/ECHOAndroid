package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import app.echo.android.model.playback.*

/** Owned by the playback service. No per-buffer callbacks, polling, raw exception text or media paths. */
@UnstableApi
internal class EchoAudioPipelineBinding(private val player: ExoPlayer, private val pipeline: EchoAudioPipelineState) : AnalyticsListener, AutoCloseable {
    private var value = EchoDecoderDiagnostics()
    private var trackConfig: AudioSink.AudioTrackConfig? = null
    private var closed = false
    private var pendingCodecFailure: EchoDecoderFailureStage? = null
    private val sessionProvider: () -> Int? = { if (!closed && value.audioEnabled && trackConfig != null)
        player.audioSessionId.takeIf { it > 0 } else null }
    init {
        pipeline.systemEffectsSession = sessionProvider
        pipeline.decoder(value)
        player.addAnalyticsListener(this)
    }
    private fun publish(next: EchoDecoderDiagnostics) {
        value = next
        if (!closed && pipeline.systemEffectsSession === sessionProvider) pipeline.decoder(next)
    }
    override fun onAudioEnabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
        pendingCodecFailure = null
        publish(EchoDecoderDiagnostics(audioEnabled = true))
    }
    override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String,
        initializedTimestampMs: Long, initializationDurationMs: Long) {
        pendingCodecFailure = null
        publish(value.copy(name = decoderName.take(160), implementation = EchoDecoderDiagnostics.implementation(decoderName),
            initializationDurationMs = initializationDurationMs.coerceAtLeast(0), initializationCount = value.initializationCount + 1,
            initializationRecoveryObserved = value.initializationFailureCount > 0))
    }
    override fun onAudioDecoderReleased(eventTime: AnalyticsListener.EventTime, decoderName: String) {
        if (value.name == decoderName.take(160)) publish(value.copy(name = null, implementation = EchoDecoderImplementation.Unreported))
    }
    override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
        publish(value.copy(audioSessionId = audioSessionId.takeIf { it > 0 }))
    }
    override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) {
        // The custom USB AudioOutputProvider can emit the same sink lifecycle callback.
        // Its configuration is not proof of a system AudioTrack instance.
        if (EchoPlaybackProcessRuntime.usbExclusiveEnabled || EchoPlaybackProcessRuntime.usbBitPerfectEnabled) {
            trackConfig = null
            publish(value.copy(systemOutput = null, audioSessionId = null))
            return
        }
        trackConfig = audioTrackConfig
        val bits = pcmBits(audioTrackConfig.encoding)
        val format = if (bits != null) EchoPcmReadout(audioTrackConfig.sampleRate, bits,
            Integer.bitCount(audioTrackConfig.channelConfig), audioTrackConfig.encoding == C.ENCODING_PCM_FLOAT) else null
        publish(value.copy(systemOutput = format?.takeIf { it.valid }, systemOffloaded = audioTrackConfig.offload))
    }
    override fun onAudioTrackReleased(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) {
        if (trackConfig === audioTrackConfig) { trackConfig = null; publish(value.copy(systemOutput = null, audioSessionId = null)) }
    }
    override fun onAudioCodecError(eventTime: AnalyticsListener.EventTime, audioCodecError: Exception) {
        val initialization = audioCodecError is MediaCodecRenderer.DecoderInitializationException
        val stage = when {
            initialization -> EchoDecoderFailureStage.Initialization
            value.name != null -> EchoDecoderFailureStage.Decoding
            else -> EchoDecoderFailureStage.CodecError
        }
        pendingCodecFailure = stage
        failure(stage, audioCodecError.javaClass.simpleName)
    }
    override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
        val stage = when (error.errorCode) {
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> EchoDecoderFailureStage.Initialization
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> EchoDecoderFailureStage.Selection
            PlaybackException.ERROR_CODE_DECODING_FAILED -> EchoDecoderFailureStage.Decoding
            else -> return
        }
        // A terminal error may repeat the codec event; retain one initialization failure for that attempt.
        if (pendingCodecFailure != stage) failure(stage, error.errorCodeName)
        pendingCodecFailure = null
    }
    private fun failure(stage: EchoDecoderFailureStage, type: String) {
        publish(value.copy(failureStage = stage, failureType = type.take(96),
            initializationFailureCount = value.initializationFailureCount + if (stage == EchoDecoderFailureStage.Initialization) 1 else 0))
    }
    override fun onAudioDisabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
        trackConfig = null
        publish(value.copy(audioEnabled = false, name = null, implementation = EchoDecoderImplementation.Unreported,
            audioSessionId = null, systemOutput = null))
    }
    override fun close() {
        closed = true
        player.removeAnalyticsListener(this)
        if (pipeline.systemEffectsSession === sessionProvider) {
            pipeline.systemEffectsSession = null
            pipeline.decoder(EchoDecoderDiagnostics())
        }
    }
}

@UnstableApi
internal fun pcmBits(encoding: Int): Int? = when (encoding) {
    C.ENCODING_PCM_8BIT -> 8
    C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
    C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
    C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 32
    else -> null
}
