package app.echo.android.feature.settings

import app.echo.android.model.playback.EchoBitPerfectState
import app.echo.android.model.playback.EchoOutputDeviceKind
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.isDsdSource

internal enum class DacAssessment {
    PlaybackError, NoUsb, Bluetooth, PermissionPending, PermissionRequired,
    ExclusiveDisabled, WaitingPlayback, TransportError, ExclusivePending,
    UnsupportedSource, UnsupportedFormat, ClockUnverified, VolumeChanged,
    Direct, Processing, BitPerfectPending,
}

internal fun EchoPlaybackStatus.hasLiveUsbOutput(): Boolean =
    isPlaying && state == EchoPlaybackState.Playing && diagnostics.usbExclusiveStreaming &&
        diagnostics.usbConnected && diagnostics.usbExclusiveEnabled &&
        diagnostics.usbHostPermissionGranted && !diagnostics.usbHostPermissionPending

internal fun EchoPlaybackStatus.hasVerifiedUsbDirect(): Boolean =
    hasLiveUsbOutput() && diagnostics.usbBitPerfectEnabled &&
        diagnostics.bitPerfectState == EchoBitPerfectState.Direct

/** Choose one actionable obstacle; a requested rate or stale direct state is never proof. */
internal fun assessDac(status: EchoPlaybackStatus): DacAssessment {
    val d = status.diagnostics
    return when {
        status.state == EchoPlaybackState.Error -> DacAssessment.PlaybackError
        !d.usbConnected -> if (EchoOutputDeviceKind.fromId(d.outputDeviceKind) == EchoOutputDeviceKind.Bluetooth)
            DacAssessment.Bluetooth else DacAssessment.NoUsb
        !d.usbExclusiveEnabled -> DacAssessment.ExclusiveDisabled
        d.usbHostPermissionPending -> DacAssessment.PermissionPending
        !d.usbHostPermissionGranted -> DacAssessment.PermissionRequired
        !status.isPlaying || status.state != EchoPlaybackState.Playing -> DacAssessment.WaitingPlayback
        !d.usbExclusiveStreaming -> if (d.usbLastRequestError != null || d.usbAudioDescriptorError != null)
            DacAssessment.TransportError else DacAssessment.ExclusivePending
        !d.usbBitPerfectEnabled -> DacAssessment.Processing
        else -> when (d.bitPerfectState) {
            EchoBitPerfectState.Direct -> DacAssessment.Direct
            EchoBitPerfectState.UnsupportedSource -> DacAssessment.UnsupportedSource
            EchoBitPerfectState.UnsupportedFormat -> DacAssessment.UnsupportedFormat
            EchoBitPerfectState.ClockUnverified -> DacAssessment.ClockUnverified
            EchoBitPerfectState.VolumeChanged -> DacAssessment.VolumeChanged
            EchoBitPerfectState.TransportError, EchoBitPerfectState.UsbUnavailable -> DacAssessment.TransportError
            EchoBitPerfectState.PlaybackError -> DacAssessment.PlaybackError
            EchoBitPerfectState.Off, EchoBitPerfectState.Waiting -> DacAssessment.BitPerfectPending
        }
    }
}

internal fun EchoPlaybackStatus.hasDecodedRateChange(): Boolean {
    val d = diagnostics
    if (!isPlaying || state != EchoPlaybackState.Playing || d.isDsdSource()) return false
    val source = d.sampleRateHz?.takeIf { it > 0 } ?: return false
    val decoded = d.decodedSampleRateHz?.takeIf { it > 0 } ?: return false
    return source != decoded
}
