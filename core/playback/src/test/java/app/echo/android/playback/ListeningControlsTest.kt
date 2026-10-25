package app.echo.android.playback

import app.echo.android.model.playback.*
import org.junit.Assert.*
import org.junit.Test

class ListeningControlsTest {
    private val identity = EchoAudioResumeIdentity("one", 0, "usb|dac-a")
    @Test fun transientResumeRequiresSameSongOutputAndStillValidUserIntent() {
        val guard = EchoAudioResumeGuard()
        guard.remember(identity, null)
        assertTrue(guard.consume(identity, true, 1))
        assertFalse(guard.consume(identity, true, 2)) // A ticket is used only once.
        guard.remember(identity, null)
        guard.cancel() // Manual pause, including a pause while already paused.
        assertFalse(guard.consume(identity, true, 2))
        guard.remember(identity, null)
        assertFalse(guard.consume(identity.copy(route = "bluetooth|other"), true, 2))
        guard.remember(identity, null)
        assertFalse(guard.consume(identity.copy(mediaId = "two"), true, 2))
        guard.remember(identity, null)
        assertFalse(guard.consume(identity, false, 2)) // Error, ended state, or lost authorization.
        guard.remember(identity, 100)
        assertFalse(guard.consume(identity, true, 100)) // Expired timer cannot be resurrected by focus gain.
    }

    @Test fun timerWaitsOnlyForAPlayingFiniteSong() {
        assertTrue(EchoSleepTimerPolicy.shouldFinishCurrentTrack(true, true, 200_000, 70_000, false))
        assertFalse(EchoSleepTimerPolicy.shouldFinishCurrentTrack(false, true, 200_000, 70_000, false))
        assertFalse(EchoSleepTimerPolicy.shouldFinishCurrentTrack(true, false, 200_000, 70_000, false))
        assertFalse(EchoSleepTimerPolicy.shouldFinishCurrentTrack(true, true, 0, 70_000, false))
        assertFalse(EchoSleepTimerPolicy.shouldFinishCurrentTrack(true, true, 200_000, 70_000, true))
        assertFalse(EchoSleepTimerPolicy.shouldFinishCurrentTrack(true, true, 200_000, 200_000, false))
        assertEquals(1f, EchoSleepTimerPolicy.fadeMultiplier(0, allowFade = false), 0f)
        assertTrue(EchoSleepTimerPolicy.shouldPauseAtEndOfMediaItem(EchoSleepTimerMode.EndOfTrack))
    }

    @Test fun hidingRemovesEveryQueueOccurrenceAndRestoresShuffleIndices() {
        val ranges = HiddenQueuePolicy.ranges(5, { listOf("one", "hidden", "hidden", "two", "hidden")[it] }, setOf("hidden"))
        assertEquals(listOf(QueueRemovalRange(1, 3), QueueRemovalRange(4, 5)), ranges)
        val items = listOf("one", "hidden", "two").map { EchoTrackRef(id = it, uri = "file:/$it", title = it, artist = "artist") }
        val session = EchoPlaybackSessionSnapshot(items, 1, 5000, true, shuffleEnabled = true, shuffleOrder = listOf(2, 1, 0))
        val restored = session.withoutHiddenTracks(setOf("hidden"))!!
        assertEquals(listOf("one", "two"), restored.queue.map { it.id })
        assertEquals(1, restored.currentIndex)
        assertEquals(0L, restored.positionMs)
        assertFalse(restored.playWhenReady)
        assertEquals(listOf(1, 0), restored.shuffleOrder)
        assertNull(session.withoutHiddenTracks(items.map { it.id }.toSet()))
        val unaffected = session.copy(currentIndex = 0).withoutHiddenTracks(setOf("hidden"))!!
        assertEquals(5000L, unaffected.positionMs)
        assertTrue(unaffected.playWhenReady)
    }
}
