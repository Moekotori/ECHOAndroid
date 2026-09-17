package app.echo.android.model.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoAudioRoutePlaybackPolicyTest {
    private val pauseOnly = EchoAudioRoutePlaybackOptions(pauseOnDisconnect = true, resumeOnReconnect = false)
    private val pauseAndResume = EchoAudioRoutePlaybackOptions(pauseOnDisconnect = true, resumeOnReconnect = true)
    private val continueOnSpeaker = EchoAudioRoutePlaybackOptions(pauseOnDisconnect = false, resumeOnReconnect = true)

    @Test
    fun wiredBluetoothAndUsbCountAsExternal() {
        assertTrue(EchoAudioRoutePlaybackPolicy.isExternalOutput(EchoOutputDeviceKind.Wired))
        assertTrue(EchoAudioRoutePlaybackPolicy.isExternalOutput(EchoOutputDeviceKind.Bluetooth))
        assertTrue(EchoAudioRoutePlaybackPolicy.isExternalOutput(EchoOutputDeviceKind.Usb))
        assertFalse(EchoAudioRoutePlaybackPolicy.isExternalOutput(EchoOutputDeviceKind.Speaker))
        assertFalse(EchoAudioRoutePlaybackPolicy.isExternalOutput(EchoOutputDeviceKind.System))
    }

    @Test
    fun becomingNoisyPausesOnlyWhenEnabledAndPlaying() {
        assertTrue(EchoAudioRoutePlaybackPolicy.shouldPauseForBecomingNoisy(pauseOnly, playWhenReady = true))
        assertFalse(EchoAudioRoutePlaybackPolicy.shouldPauseForBecomingNoisy(pauseOnly, playWhenReady = false))
        assertFalse(EchoAudioRoutePlaybackPolicy.shouldPauseForBecomingNoisy(continueOnSpeaker, playWhenReady = true))
    }

    @Test
    fun routeLossPausesWhenLeavingExternalOutput() {
        assertTrue(
            EchoAudioRoutePlaybackPolicy.shouldPauseForRouteLoss(
                pauseOnly,
                playWhenReady = true,
                previous = EchoOutputDeviceKind.Bluetooth,
                current = EchoOutputDeviceKind.Speaker,
            ),
        )
        assertFalse(
            EchoAudioRoutePlaybackPolicy.shouldPauseForRouteLoss(
                pauseOnly,
                playWhenReady = true,
                previous = EchoOutputDeviceKind.Bluetooth,
                current = EchoOutputDeviceKind.Usb,
            ),
        )
        assertFalse(
            EchoAudioRoutePlaybackPolicy.shouldPauseForRouteLoss(
                continueOnSpeaker,
                playWhenReady = true,
                previous = EchoOutputDeviceKind.Wired,
                current = EchoOutputDeviceKind.Speaker,
            ),
        )
    }

    @Test
    fun reconnectResumesOnlyAfterDisconnectPause() {
        assertTrue(
            EchoAudioRoutePlaybackPolicy.shouldResumeForRouteGain(
                pauseAndResume,
                pausedByDisconnect = true,
                previous = EchoOutputDeviceKind.Speaker,
                current = EchoOutputDeviceKind.Bluetooth,
                canResume = true,
            ),
        )
        assertFalse(
            EchoAudioRoutePlaybackPolicy.shouldResumeForRouteGain(
                pauseOnly,
                pausedByDisconnect = true,
                previous = EchoOutputDeviceKind.Speaker,
                current = EchoOutputDeviceKind.Bluetooth,
                canResume = true,
            ),
        )
        assertFalse(
            EchoAudioRoutePlaybackPolicy.shouldResumeForRouteGain(
                pauseAndResume,
                pausedByDisconnect = false,
                previous = EchoOutputDeviceKind.Speaker,
                current = EchoOutputDeviceKind.Bluetooth,
                canResume = true,
            ),
        )
        assertFalse(
            EchoAudioRoutePlaybackPolicy.shouldResumeForRouteGain(
                pauseAndResume,
                pausedByDisconnect = true,
                previous = EchoOutputDeviceKind.Speaker,
                current = EchoOutputDeviceKind.Bluetooth,
                canResume = false,
            ),
        )
    }
}
