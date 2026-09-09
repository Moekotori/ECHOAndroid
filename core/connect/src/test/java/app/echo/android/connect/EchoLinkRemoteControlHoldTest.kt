package app.echo.android.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoLinkRemoteControlHoldTest {
    @Test
    fun staleRemotePositionIsNotShownAfterSeekCommit() {
        val committed = 60_000L
        val staleRemote = 8_000L
        assertTrue(
            EchoLinkRemoteControlHold.shouldHoldCommittedPosition(
                committedPositionMs = committed,
                committedAtElapsedMs = 1_000L,
                remotePositionMs = staleRemote,
                nowElapsedMs = 1_400L,
            ),
        )
        assertEquals(
            committed,
            EchoLinkRemoteControlHold.displayedPositionMs(
                remotePositionMs = staleRemote,
                livePositionMs = staleRemote + 400L,
                committedPositionMs = committed,
                committedAtElapsedMs = 1_000L,
                nowElapsedMs = 1_400L,
                draggingPositionMs = null,
            ),
        )
    }

    @Test
    fun matchingRemotePositionReleasesTheHold() {
        assertFalse(
            EchoLinkRemoteControlHold.shouldHoldCommittedPosition(
                committedPositionMs = 60_000L,
                committedAtElapsedMs = 1_000L,
                remotePositionMs = 60_400L,
                nowElapsedMs = 1_500L,
            ),
        )
        assertEquals(
            60_400L,
            EchoLinkRemoteControlHold.displayedPositionMs(
                remotePositionMs = 60_400L,
                livePositionMs = 60_400L,
                committedPositionMs = 60_000L,
                committedAtElapsedMs = 1_000L,
                nowElapsedMs = 1_500L,
                draggingPositionMs = null,
            ),
        )
    }

    @Test
    fun staleRemoteVolumeIsNotShownAfterCommit() {
        assertTrue(
            EchoLinkRemoteControlHold.shouldHoldCommittedVolume(
                committedVolume = 0.2f,
                committedAtElapsedMs = 500L,
                remoteVolume = 0.8f,
                nowElapsedMs = 800L,
            ),
        )
        assertEquals(
            0.2f,
            EchoLinkRemoteControlHold.displayedVolume(
                remoteVolume = 0.8f,
                committedVolume = 0.2f,
                committedAtElapsedMs = 500L,
                nowElapsedMs = 800L,
                draggingVolume = null,
            ),
            0.0001f,
        )
    }

    @Test
    fun dragOverridesHold() {
        assertEquals(
            12_000L,
            EchoLinkRemoteControlHold.displayedPositionMs(
                remotePositionMs = 1_000L,
                livePositionMs = 1_000L,
                committedPositionMs = 50_000L,
                committedAtElapsedMs = 10L,
                nowElapsedMs = 20L,
                draggingPositionMs = 12_000L,
            ),
        )
    }
}
