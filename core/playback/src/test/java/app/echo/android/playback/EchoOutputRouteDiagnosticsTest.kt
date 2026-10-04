package app.echo.android.playback

import app.echo.android.model.playback.EchoOutputDeviceKind
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import org.junit.Assert.*
import org.junit.Test

class EchoOutputRouteDiagnosticsTest {
    @Test fun mixerRoutesPreserveAllActuallyReportedOutputs() {
        val result = EchoPlaybackDiagnostics().withOutputRoute(EchoOutputRoute(
            kind = EchoOutputDeviceKind.Bluetooth.id, deviceName = "Headset", verified = true,
            deviceNames = listOf("Headset", "Speaker"),
        ))
        assertTrue(result.outputRouteVerified)
        assertEquals(listOf("Headset", "Speaker"), result.routedDeviceNames)
    }
    @Test fun connectedCandidateIsNotPresentedAsActualPlaybackOutput() {
        assertFalse(EchoPlaybackDiagnostics().withOutputRoute(EchoOutputRoute(deviceName = "Connected DAC")).outputRouteVerified)
    }
    @Test fun usbExclusiveOutputWinsOverAndroidMixerRoute() {
        val result = EchoPlaybackDiagnostics(usbExclusiveStreaming = true, usbDeviceName = "Exclusive DAC")
            .withOutputRoute(EchoOutputRoute(kind = EchoOutputDeviceKind.Bluetooth.id, deviceName = "Headset", verified = true))
        assertTrue(result.outputRouteVerified)
        assertEquals(EchoOutputDeviceKind.Usb.id, result.outputDeviceKind)
        assertEquals(listOf("Exclusive DAC"), result.routedDeviceNames)
    }
}
