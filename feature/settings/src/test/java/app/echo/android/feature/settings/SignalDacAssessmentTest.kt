package app.echo.android.feature.settings

import app.echo.android.model.playback.EchoBitPerfectState
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalDacAssessmentTest {
    private val direct = EchoPlaybackStatus(
        state = EchoPlaybackState.Playing,
        isPlaying = true,
        diagnostics = EchoPlaybackDiagnostics(
            usbConnected = true, usbHostPermissionGranted = true, usbExclusiveEnabled = true,
            usbExclusiveStreaming = true, usbBitPerfectEnabled = true,
            bitPerfectState = EchoBitPerfectState.Direct, sampleRateHz = 44100,
        ),
    )

    @Test
    fun requestAndOldDirectStateDoNotVerifyLiveOutput() {
        assertTrue(direct.hasVerifiedUsbDirect())
        val notStreaming = direct.copy(diagnostics = direct.diagnostics.copy(
            usbExclusiveStreaming = false, usbLastRequestedSampleRateHz = 44100,
        ))
        assertFalse(notStreaming.hasVerifiedUsbDirect())
        assertEquals(DacAssessment.ExclusivePending, assessDac(notStreaming))
        listOf(EchoPlaybackState.Paused, EchoPlaybackState.Buffering, EchoPlaybackState.Seeking).forEach {
            val inactive = direct.copy(state = it)
            assertFalse(inactive.hasVerifiedUsbDirect())
            assertEquals(DacAssessment.WaitingPlayback, assessDac(inactive))
        }
        assertFalse(direct.copy(isPlaying = false).hasVerifiedUsbDirect())
        val disconnected = direct.copy(diagnostics = direct.diagnostics.copy(usbConnected = false))
        assertFalse(disconnected.hasVerifiedUsbDirect())
        assertEquals(DacAssessment.NoUsb, assessDac(disconnected))
    }

    @Test
    fun permissionAndProcessingModesHaveDifferentRecovery() {
        assertEquals(DacAssessment.PermissionPending, assessDac(direct.copy(diagnostics =
            direct.diagnostics.copy(usbHostPermissionGranted = false, usbHostPermissionPending = true))))
        assertEquals(DacAssessment.PermissionRequired, assessDac(direct.copy(diagnostics =
            direct.diagnostics.copy(usbHostPermissionGranted = false))))
        val processing = direct.copy(diagnostics = direct.diagnostics.copy(usbBitPerfectEnabled = false))
        assertEquals(DacAssessment.Processing, assessDac(processing))
        assertFalse(processing.hasVerifiedUsbDirect())
        val compatible = direct.copy(diagnostics = direct.diagnostics.copy(bitPerfectState = EchoBitPerfectState.Compatible))
        assertEquals(DacAssessment.Compatible, assessDac(compatible))
        assertFalse(compatible.hasVerifiedUsbDirect())
        assertFalse(compatible.diagnostics.usbVolumeLocked)
        assertTrue(direct.diagnostics.usbVolumeLocked)
        assertTrue(direct.diagnostics.copy(bitPerfectState = EchoBitPerfectState.Waiting).usbVolumeLocked)
    }

    @Test
    fun signalReadoutSeparatesLiveDirectAndCompatibleFromStaleOutput() {
        val compatible = direct.copy(diagnostics = direct.diagnostics.copy(bitPerfectState = EchoBitPerfectState.Compatible))
        assertEquals(EchoBitPerfectState.Direct, direct.signalBitPerfectState())
        assertEquals(EchoBitPerfectState.Compatible, compatible.signalBitPerfectState())
        for (status in listOf(direct, compatible)) {
            val paused = status.copy(state = EchoPlaybackState.Paused, isPlaying = false)
            val disconnected = status.copy(diagnostics = status.diagnostics.copy(usbConnected = false))
            val disabled = status.copy(diagnostics = status.diagnostics.copy(usbBitPerfectEnabled = false))
            assertEquals(EchoBitPerfectState.Waiting, paused.signalBitPerfectState())
            assertEquals(EchoBitPerfectState.Waiting, disconnected.signalBitPerfectState())
            assertEquals(EchoBitPerfectState.Off, disabled.signalBitPerfectState())
        }
    }

    @Test
    fun strictFailuresRemainActionableDuringExclusiveStreaming() {
        val expected = mapOf(
            EchoBitPerfectState.UnsupportedSource to DacAssessment.UnsupportedSource,
            EchoBitPerfectState.UnsupportedFormat to DacAssessment.UnsupportedFormat,
            EchoBitPerfectState.DopConfigurationRequired to DacAssessment.DopConfigurationRequired,
            EchoBitPerfectState.ClockUnverified to DacAssessment.ClockUnverified,
            EchoBitPerfectState.VolumeChanged to DacAssessment.VolumeChanged,
            EchoBitPerfectState.TransportError to DacAssessment.TransportError,
        )
        expected.forEach { (state, assessment) ->
            val status = direct.copy(diagnostics = direct.diagnostics.copy(bitPerfectState = state))
            assertEquals(assessment, assessDac(status))
            assertFalse(status.hasVerifiedUsbDirect())
            val failed = status.copy(state = EchoPlaybackState.Error, isPlaying = false,
                diagnostics = status.diagnostics.copy(usbExclusiveStreaming = false))
            assertEquals(assessment, assessDac(failed))
            assertFalse(failed.hasVerifiedUsbDirect())
            assertEquals(DacAssessment.PlaybackError, assessDac(failed.copy(
                diagnostics = failed.diagnostics.copy(usbBitPerfectEnabled = false))))
        }
    }

    @Test
    fun dsdConversionAndUnknownRatesAreNotPcmRateMismatch() {
        val changed = direct.copy(diagnostics = direct.diagnostics.copy(decodedSampleRateHz = 48000))
        assertTrue(changed.hasDecodedRateChange())
        assertFalse(changed.copy(isPlaying = false).hasDecodedRateChange())
        assertFalse(direct.hasDecodedRateChange())
        assertFalse(changed.copy(diagnostics = changed.diagnostics.copy(sampleRateHz = 0)).hasDecodedRateChange())
        assertFalse(changed.copy(diagnostics = changed.diagnostics.copy(
            codec = "DSD", sampleRateHz = 2822400, decodedSampleRateHz = 176400,
        )).hasDecodedRateChange())
    }
}
