package app.echo.android.playback

import org.junit.Assert.*
import org.junit.Test

class EchoTrackFadePolicyTest {
    @Test fun disabledEnvelopeLeavesEveryPositionUnchanged() {
        for (position in listOf(0L, 250L, 5000L, 10000L)) {
            assertEquals(1f, EchoTrackFadePolicy.gain(position, 10000, 1500, false), 0f)
        }
    }

    @Test fun fadesAreSmoothSymmetricAndReachUnity() {
        assertEquals(0f, EchoTrackFadePolicy.gain(0, 10000, 1000, true), 0.0001f)
        assertEquals(0.5f, EchoTrackFadePolicy.gain(500, 10000, 1000, true), 0.0001f)
        assertEquals(1f, EchoTrackFadePolicy.gain(1000, 10000, 1000, true), 0.0001f)
        assertEquals(1f, EchoTrackFadePolicy.gain(5000, 10000, 1000, true), 0.0001f)
        assertEquals(0.5f, EchoTrackFadePolicy.gain(9500, 10000, 1000, true), 0.0001f)
        assertEquals(0f, EchoTrackFadePolicy.gain(10000, 10000, 1000, true), 0.0001f)
    }

    @Test fun shortTracksReachFullVolumeAtTheirMiddle() {
        assertEquals(1f, EchoTrackFadePolicy.gain(150, 300, 1500, true), 0.0001f)
        assertEquals(0f, EchoTrackFadePolicy.gain(300, 300, 1500, true), 0.0001f)
    }

    @Test fun unknownDurationDoesNotInventAnEnding() {
        assertEquals(0.5f, EchoTrackFadePolicy.gain(500, -1, 1000, true), 0.0001f)
        assertEquals(1f, EchoTrackFadePolicy.gain(90000, -1, 1000, true), 0.0001f)
        assertNull(EchoTrackFadePolicy.nextDelayMs(90000, -1, 1000, 1f))
    }

    @Test fun schedulerSleepsOutsideFadeWindowsAndAccountsForSpeed() {
        assertEquals(20L, EchoTrackFadePolicy.nextDelayMs(100, 180000, 1500, 1f))
        assertEquals(87750L, EchoTrackFadePolicy.nextDelayMs(3000, 180000, 1500, 2f))
        assertEquals(20L, EchoTrackFadePolicy.nextDelayMs(179000, 180000, 1500, 1f))
    }
}
