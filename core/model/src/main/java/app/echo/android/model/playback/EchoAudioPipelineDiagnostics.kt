package app.echo.android.model.playback

/** Platform means MediaCodec, including software implementations. It does not imply hardware decoding. */
enum class EchoDecoderImplementation { Unreported, Platform, Ffmpeg, PcmCopy, DsdDop, Other }
enum class EchoDecoderFailureStage { Selection, Initialization, Decoding, CodecError }

data class EchoPcmReadout(val sampleRateHz: Int, val bitDepth: Int, val channelCount: Int, val floatingPoint: Boolean = false) {
    val valid: Boolean get() = sampleRateHz > 0 && bitDepth in 1..64 && channelCount in 1..32
}

data class EchoDecoderDiagnostics(
    val name: String? = null,
    val implementation: EchoDecoderImplementation = EchoDecoderImplementation.Unreported,
    val audioEnabled: Boolean = false,
    val audioSessionId: Int? = null,
    val systemOutput: EchoPcmReadout? = null,
    val systemOffloaded: Boolean = false,
    val initializationDurationMs: Long? = null,
    val initializationCount: Int = 0,
    val initializationFailureCount: Int = 0,
    val failureStage: EchoDecoderFailureStage? = null,
    val failureType: String? = null,
    val initializationRecoveryObserved: Boolean = false,
) {
    companion object {
        fun implementation(name: String): EchoDecoderImplementation = when {
            name == "EchoRawPcm" -> EchoDecoderImplementation.PcmCopy
            name == "EchoDsdDop" || name == "EchoDsdDopWorker" -> EchoDecoderImplementation.DsdDop
            name.startsWith("ffmpeg", ignoreCase = true) -> EchoDecoderImplementation.Ffmpeg
            name.startsWith("c2.", ignoreCase = true) || name.startsWith("OMX.", ignoreCase = true) -> EchoDecoderImplementation.Platform
            else -> EchoDecoderImplementation.Other
        }
    }
}

enum class EchoPcmPrecisionChange { Unchanged, IntegerWidening, IntegerReduction, FloatingPointConversion, Unknown }

/** Converter negotiation only: not a DAC FIFO, an audible clock, or a hardware readback. */
data class EchoUsbConversionDiagnostics(
    val input: EchoPcmReadout,
    val output: EchoPcmReadout,
    val requestedSampleRateHz: Int = 0,
    val requestedBitDepth: Int = 0,
    val requestedChannelCount: Int = 0,
) {
    val resampled: Boolean get() = input.sampleRateHz != output.sampleRateHz
    val channelsChanged: Boolean get() = input.channelCount != output.channelCount
    val precision: EchoPcmPrecisionChange get() = when {
        !input.valid || !output.valid -> EchoPcmPrecisionChange.Unknown
        input.floatingPoint != output.floatingPoint -> EchoPcmPrecisionChange.FloatingPointConversion
        input.floatingPoint -> if (input.bitDepth == output.bitDepth) EchoPcmPrecisionChange.Unchanged else EchoPcmPrecisionChange.Unknown
        input.bitDepth < output.bitDepth -> EchoPcmPrecisionChange.IntegerWidening
        input.bitDepth > output.bitDepth -> EchoPcmPrecisionChange.IntegerReduction
        else -> EchoPcmPrecisionChange.Unchanged
    }
}

data class EchoAudioPipelineDiagnostics(
    val decoder: EchoDecoderDiagnostics = EchoDecoderDiagnostics(),
    val usbConversion: EchoUsbConversionDiagnostics? = null,
)

enum class EchoSystemEffectsAvailability { Available, RemotePlayback, DedicatedUsb, NoSession, NoSystemOutput, Offloaded }
fun EchoPlaybackDiagnostics.systemEffectsAvailability(localPlayback: Boolean = true): EchoSystemEffectsAvailability = when {
    !localPlayback -> EchoSystemEffectsAvailability.RemotePlayback
    usbExclusiveEnabled || usbBitPerfectEnabled || usbExclusiveStreaming -> EchoSystemEffectsAvailability.DedicatedUsb
    !decoder.audioEnabled || decoder.audioSessionId == null || decoder.audioSessionId <= 0 -> EchoSystemEffectsAvailability.NoSession
    offloadActive || decoder.systemOffloaded -> EchoSystemEffectsAvailability.Offloaded
    decoder.systemOutput == null -> EchoSystemEffectsAvailability.NoSystemOutput
    else -> EchoSystemEffectsAvailability.Available
}

fun EchoPlaybackDiagnostics.withAudioPipeline(readout: EchoAudioPipelineDiagnostics) = copy(
    decoder = readout.decoder, usbConversion = readout.usbConversion,
)
