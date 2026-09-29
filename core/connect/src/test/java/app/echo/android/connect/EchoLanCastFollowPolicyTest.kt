package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteStreamItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoLanCastFollowPolicyTest {
    private val items = listOf("a", "b", "c").map {
        EchoRemoteStreamItem(id = it, streamUrl = "http://10.0.0.2:8090/cast/$it", title = it, artist = "x")
    }

    @Test
    fun sameUriMeansNoAdvance() {
        assertNull(EchoLanCastFollowPolicy.indexForTrackUri(items, 0, "http://10.0.0.2:8090/cast/a"))
    }

    @Test
    fun rendererAdvancedToNext() {
        assertEquals(1, EchoLanCastFollowPolicy.indexForTrackUri(items, 0, "http://10.0.0.2:8090/cast/b"))
    }

    @Test
    fun unknownUriIsIgnored() {
        assertNull(EchoLanCastFollowPolicy.indexForTrackUri(items, 0, "http://elsewhere/x"))
        assertNull(EchoLanCastFollowPolicy.indexForTrackUri(items, 0, null))
    }

    @Test
    fun stopNearEndCountsAsFinished() {
        assertTrue(EchoLanCastFollowPolicy.dlnaTrackEnded("STOPPED", true, false, 238_000L, 240_000L))
    }

    @Test
    fun stopMidTrackIsNotFinished() {
        assertFalse(EchoLanCastFollowPolicy.dlnaTrackEnded("STOPPED", true, false, 60_000L, 240_000L))
    }

    @Test
    fun pausedOrAlreadyStoppedIsNotFinished() {
        assertFalse(EchoLanCastFollowPolicy.dlnaTrackEnded("PAUSED_PLAYBACK", true, false, 239_000L, 240_000L))
        assertFalse(EchoLanCastFollowPolicy.dlnaTrackEnded("STOPPED", false, false, 239_000L, 240_000L))
        assertFalse(EchoLanCastFollowPolicy.dlnaTrackEnded("STOPPED", true, true, 239_000L, 240_000L))
    }

    @Test
    fun extrapolationStopsAtDuration() {
        assertEquals(10_500L, EchoLanCastFollowPolicy.extrapolatedPositionMs(10_000L, 1_000L, 1_500L, true, 60_000L))
        assertEquals(60_000L, EchoLanCastFollowPolicy.extrapolatedPositionMs(59_000L, 0L, 5_000L, true, 60_000L))
        assertEquals(10_000L, EchoLanCastFollowPolicy.extrapolatedPositionMs(10_000L, 0L, 5_000L, false, 60_000L))
    }
}
