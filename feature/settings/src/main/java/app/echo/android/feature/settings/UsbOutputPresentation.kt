package app.echo.android.feature.settings

import app.echo.android.model.playback.EchoPlaybackStatus

/** Only live strict-path evidence can populate the measured output readouts. */
internal data class UsbOutputPresentation(
    val connected: Boolean,
    val deviceName: String?,
    val audioClass: String?,
    val permissionGranted: Boolean,
    val permissionPending: Boolean,
    val streaming: Boolean,
    val verified: Boolean,
    val assessment: DacAssessment,
    val clockHz: Int?,
    val outputBits: Int?,
    val canTest: Boolean,
    val transferRateHz: Int? = null,
    val transferBits: Int? = null,
    val bufferDurationMs: Long? = null,
) {
    val failed: Boolean get() = when (assessment) {
        DacAssessment.PlaybackError, DacAssessment.TransportError, DacAssessment.UnsupportedSource,
        DacAssessment.DopConfigurationRequired, DacAssessment.UnsupportedFormat, DacAssessment.ClockUnverified, DacAssessment.VolumeChanged -> true
        else -> false
    }

    fun headlineResource(): Int = when {
        !connected -> R.string.diag_usb_not_connected
        assessment == DacAssessment.ClockUnverified -> R.string.usb_page_clock_unverified
        failed -> R.string.usb_page_output_stopped
        verified -> R.string.dac_status_direct_short
        streaming -> R.string.dac_status_exclusive_short
        permissionPending -> R.string.diag_usb_wait_permission
        !permissionGranted -> R.string.diag_unauthorized
        assessment == DacAssessment.ExclusiveDisabled -> R.string.diag_usb_exclusive_off
        assessment == DacAssessment.WaitingPlayback -> R.string.usb_page_waiting_playback
        else -> R.string.diag_usb_granted_idle
    }
}

internal fun EchoPlaybackStatus.usbOutputPresentation(): UsbOutputPresentation {
    val d = diagnostics
    val live = hasLiveUsbOutput()
    val verified = hasVerifiedUsbDirect()
    return UsbOutputPresentation(
        connected = d.usbConnected,
        deviceName = d.usbDeviceName?.takeIf { d.usbConnected && it.isNotBlank() },
        audioClass = d.usbAudioClass?.takeIf { d.usbConnected && it.isNotBlank() },
        permissionGranted = d.usbConnected && d.usbHostPermissionGranted,
        permissionPending = d.usbConnected && !d.usbHostPermissionGranted && d.usbHostPermissionPending,
        streaming = live,
        verified = verified,
        assessment = if (d.usbConnected) assessDac(this) else DacAssessment.NoUsb,
        clockHz = d.bitPerfectSampleRateHz?.takeIf { verified && it > 0 },
        outputBits = d.bitPerfectOutputBits?.takeIf { verified && it > 0 },
        canTest = d.usbConnected && d.usbHostPermissionGranted && !isPlaying && !d.usbExclusiveStreaming &&
            state != app.echo.android.model.playback.EchoPlaybackState.Loading &&
            state != app.echo.android.model.playback.EchoPlaybackState.Seeking &&
            state != app.echo.android.model.playback.EchoPlaybackState.Buffering,
        transferRateHz = d.usbOutputSampleRateHz?.takeIf { live && it > 0 },
        transferBits = d.usbOutputBitDepth?.takeIf { live && it > 0 },
        bufferDurationMs = d.usbBufferCapacityFrames?.takeIf { live && it > 0 }?.let { frames ->
            d.usbOutputSampleRateHz?.takeIf { it > 0 }?.let { frames * 1000L / it }
        },
    )
}
