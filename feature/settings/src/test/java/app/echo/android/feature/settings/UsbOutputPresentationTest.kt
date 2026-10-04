package app.echo.android.feature.settings

import app.echo.android.model.playback.EchoBitPerfectState
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbOutputPresentationTest {
    private val playing = EchoPlaybackStatus(
        state = EchoPlaybackState.Playing, isPlaying = true,
        diagnostics = EchoPlaybackDiagnostics(
            usbConnected = true, usbDeviceName = "TEAC HA-P90SD", usbHostPermissionGranted = true,
            usbExclusiveEnabled = true, usbExclusiveStreaming = true, usbBitPerfectEnabled = true,
            bitPerfectState = EchoBitPerfectState.Direct, bitPerfectSampleRateHz = 44_100,
            bitPerfectOutputBits = 24, usbLastRequestedSampleRateHz = 48_000,
        ),
    )

    @Test fun outputReadoutsUseOnlyLiveVerifiedEvidence() {
        val live = playing.usbOutputPresentation()
        assertEquals(44_100, live.clockHz)
        assertEquals(24, live.outputBits)
        assertFalse(live.canTest)
        val inactive = listOf(
            playing.copy(isPlaying = false, state = EchoPlaybackState.Paused),
            playing.copy(diagnostics = playing.diagnostics.copy(usbExclusiveStreaming = false)),
            playing.copy(diagnostics = playing.diagnostics.copy(usbHostPermissionGranted = false)),
            playing.copy(diagnostics = playing.diagnostics.copy(usbBitPerfectEnabled = false)),
        )
        inactive.forEach {
            val output = it.usbOutputPresentation()
            assertFalse(output.verified)
            assertNull(output.clockHz)
            assertNull(output.outputBits)
        }
        assertFalse(inactive.first().usbOutputPresentation().canTest)
        assertTrue(inactive.first().copy(diagnostics = playing.diagnostics.copy(usbExclusiveStreaming = false))
            .usbOutputPresentation().canTest)
    }

    @Test fun failedClockAndDisconnectedDacHaveSpecificPresentation() {
        val failed = playing.copy(state = EchoPlaybackState.Error, isPlaying = false,
            diagnostics = playing.diagnostics.copy(bitPerfectState = EchoBitPerfectState.ClockUnverified,
                usbExclusiveStreaming = false)).usbOutputPresentation()
        assertEquals(R.string.usb_page_clock_unverified, failed.headlineResource())
        assertTrue(failed.failed)
        assertNull(failed.clockHz)
        val disconnected = playing.copy(diagnostics = playing.diagnostics.copy(usbConnected = false))
            .usbOutputPresentation()
        assertEquals(DacAssessment.NoUsb, disconnected.assessment)
        assertNull(disconnected.deviceName)
        assertFalse(disconnected.canTest)
        assertNull(disconnected.clockHz)
    }
}
