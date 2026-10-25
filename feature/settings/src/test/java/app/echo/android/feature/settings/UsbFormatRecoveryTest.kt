package app.echo.android.feature.settings

import app.echo.android.model.playback.*
import org.junit.Assert.*
import org.junit.Test

class UsbFormatRecoveryTest {
    private val connected = EchoPlaybackDiagnostics(usbExclusiveEnabled = true, usbConnected = true,
        usbHostPermissionGranted = true, usbSupportedSampleRates = listOf(48000))

    @Test fun onlyApplicableFixedFormatsOfferRecovery() {
        val fixed = EchoUsbOutputOptions(sampleRateHz = 96000)
        assertTrue(canRestoreUsbAdaptive(connected, false, fixed))
        assertFalse(canRestoreUsbAdaptive(connected, true, fixed))
        assertFalse(canRestoreUsbAdaptive(connected.copy(usbConnected = false), false, fixed))
        assertFalse(canRestoreUsbAdaptive(connected.copy(usbHostPermissionGranted = false), false, fixed))
        assertFalse(canRestoreUsbAdaptive(connected, false, EchoUsbOutputOptions()))
        assertFalse(canRestoreUsbAdaptive(connected, false, fixed.copy(sampleRateHz = 48000)))
        val failed = connected.copy(usbSupportedSampleRates = emptyList(), usbLastRequestError =
            EchoPlaybackError(EchoAudioErrorKind.UnsupportedFormat, "format", true))
        assertTrue(canRestoreUsbAdaptive(failed, false, fixed))
        assertFalse(canRestoreUsbAdaptive(failed.copy(usbLastRequestError =
            EchoPlaybackError(EchoAudioErrorKind.PermissionDenied, "permission", true)), false, fixed))
    }

    @Test fun recoveryPreservesVolumeBufferAndDeviceChoices() {
        val fixed = EchoUsbOutputOptions(96000, 24, 2, volumePercent = 35,
            bufferMode = EchoUsbBufferMode.Stable, keepAliveOnPause = false,
            dopDeviceLimits = mapOf("1:2" to 2822400), volumeMode = EchoUsbVolumeMode.Hardware)
        assertEquals(fixed.copy(sampleRateHz = 0, bitDepth = 0, channelCount = 0), fixed.withAdaptiveFormat())
    }
}
