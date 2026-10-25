package app.echo.android.feature.settings

import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.*

@Composable
internal fun decoderImplementationText(kind: EchoDecoderImplementation) = stringResource(when (kind) {
    EchoDecoderImplementation.Platform -> R.string.decoder_kind_platform
    EchoDecoderImplementation.Ffmpeg -> R.string.decoder_kind_ffmpeg
    EchoDecoderImplementation.PcmCopy -> R.string.decoder_kind_pcm
    EchoDecoderImplementation.DsdDop -> R.string.decoder_kind_dop
    EchoDecoderImplementation.Other -> R.string.decoder_kind_other
    EchoDecoderImplementation.Unreported -> R.string.diag_unreported
})

@Composable
internal fun pcmReadout(format: EchoPcmReadout): String = stringResource(
    R.string.pcm_readout, formatSampleRate(format.sampleRateHz), format.bitDepth, format.channelCount,
    stringResource(if (format.floatingPoint) R.string.pcm_float else R.string.pcm_integer))

@Composable
internal fun AudioPipelineReadouts(status: EchoPlaybackStatus) {
    val d = status.diagnostics
    val decoder = d.decoder
    val unknown = stringResource(R.string.diag_unreported)
    SignalSection(stringResource(R.string.decoder_runtime_title)) {
        SignalNote(stringResource(R.string.decoder_runtime_note))
        SignalReadout(stringResource(R.string.decoder_runtime_name), decoder.name ?: unknown)
        SignalReadout(stringResource(R.string.decoder_runtime_kind), decoderImplementationText(decoder.implementation))
        SignalReadout(stringResource(R.string.decoder_runtime_init), decoder.initializationDurationMs?.let { "$it ms" } ?: unknown)
        SignalReadout(stringResource(R.string.decoder_runtime_counts), "${decoder.initializationCount} / ${decoder.initializationFailureCount}")
        SignalReadout(stringResource(R.string.decoder_session), decoder.audioSessionId?.toString() ?: unknown)
        decoder.failureStage?.let { stage ->
            SignalReadout(stringResource(R.string.decoder_recent_failure), stringResource(when (stage) {
                EchoDecoderFailureStage.CodecError -> R.string.decoder_failure_unspecified
                EchoDecoderFailureStage.Selection -> R.string.decoder_failure_selection
                EchoDecoderFailureStage.Initialization -> R.string.decoder_failure_initialization
                EchoDecoderFailureStage.Decoding -> R.string.decoder_failure_decoding
            }))
            decoder.failureType?.let { SignalReadout(stringResource(R.string.decoder_failure_type), it) }
        }
        if (decoder.initializationRecoveryObserved) SignalNote(stringResource(R.string.decoder_recovered_note))
    }
    SignalSection(stringResource(R.string.evidence_title)) {
        SignalNote(stringResource(R.string.evidence_note))
        SignalReadout(stringResource(R.string.evidence_system_output), decoder.systemOutput?.let { pcmReadout(it) } ?: unknown)
        SignalReadout(stringResource(R.string.evidence_usb_output), if (d.usbExclusiveStreaming)
            listOfNotNull(d.usbOutputSampleRateHz?.let(::formatSampleRate), d.usbOutputBitDepth?.let { "$it bit" }).joinToString(" · ").ifBlank { unknown }
            else unknown)
        SignalReadout(stringResource(R.string.evidence_clock), stringResource(if (status.hasVerifiedUsbDirect()) R.string.evidence_clock_verified else R.string.evidence_clock_unknown))
    }
    UsbConversionReadout(d)
}

@Composable
internal fun UsbConversionReadout(d: EchoPlaybackDiagnostics) {
    val conversion = d.usbConversion
    SignalSection(stringResource(R.string.conversion_title)) {
        if (d.usbBitPerfectEnabled) {
            SignalNote(stringResource(R.string.conversion_strict))
        } else if (conversion == null || !d.usbExclusiveStreaming) {
            SignalNote(stringResource(R.string.conversion_waiting))
        } else {
            SignalNote(stringResource(R.string.conversion_evidence))
            SignalReadout(stringResource(R.string.conversion_input), pcmReadout(conversion.input))
            SignalReadout(stringResource(R.string.conversion_output), pcmReadout(conversion.output))
            SignalReadout(stringResource(R.string.conversion_precision), stringResource(when (conversion.precision) {
                EchoPcmPrecisionChange.Unchanged -> R.string.conversion_unchanged
                EchoPcmPrecisionChange.IntegerWidening -> R.string.conversion_widen
                EchoPcmPrecisionChange.IntegerReduction -> R.string.conversion_reduce
                EchoPcmPrecisionChange.FloatingPointConversion -> R.string.conversion_float
                EchoPcmPrecisionChange.Unknown -> R.string.diag_unreported
            }))
            if (conversion.resampled) SignalNote(stringResource(if (conversion.requestedSampleRateHz > 0)
                R.string.conversion_rate_requested else R.string.conversion_rate_compatible))
            if (conversion.input.bitDepth != conversion.output.bitDepth || conversion.input.floatingPoint != conversion.output.floatingPoint)
                SignalNote(stringResource(if (conversion.requestedBitDepth > 0) R.string.conversion_depth_requested else R.string.conversion_depth_compatible))
            if (conversion.channelsChanged) SignalNote(stringResource(if (conversion.requestedChannelCount > 0)
                R.string.conversion_channels else R.string.conversion_channels_compatible))
        }
    }
}
