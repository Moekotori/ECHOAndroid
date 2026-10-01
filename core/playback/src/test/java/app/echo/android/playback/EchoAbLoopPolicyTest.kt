package app.echo.android.playback

import org.junit.Assert.*
import org.junit.Test

class EchoAbLoopPolicyTest {
    @Test fun boundsRejectReversedUnknownAndTooShortLoops() {
        assertFalse(EchoAbLoopPolicy.valid(1000, 500, 10000))
        assertFalse(EchoAbLoopPolicy.valid(0, 499, 10000))
        assertFalse(EchoAbLoopPolicy.valid(-1, 1000, 10000))
        assertFalse(EchoAbLoopPolicy.valid(0, 10001, 10000))
        assertFalse(EchoAbLoopPolicy.valid(0, 1000, 0))
        assertFalse(EchoAbLoopPolicy.valid(1, Long.MIN_VALUE, 10000))
        assertTrue(EchoAbLoopPolicy.valid(9500, 10000, 10000))
    }
    @Test fun wakeupsRespectPlaybackSpeedAndStayBounded() {
        assertEquals(500L, EchoAbLoopPolicy.nextDelay(1000, 2000, 2f))
        assertEquals(1000L, EchoAbLoopPolicy.nextDelay(0, 100000, 1f))
        assertEquals(15L, EchoAbLoopPolicy.nextDelay(2001, 2000, 1f))
    }
}
