package app.echo.android.data

import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.playback.PlaybackQueueContext
import app.echo.android.model.playback.PlaybackShuffleResume
import org.junit.Assert.*
import org.junit.Test

class NextUpSessionPersistenceTest {
    @Test fun preserveDuplicateRequestsSourceAndResumeOrder() {
        val track = EchoTrackRef("song", "file:///song.flac", "Song", "Artist")
        val session = EchoSavedPlaybackSession(
            queue = listOf(
                track.copy(queueContext = PlaybackQueueContext("original", source = "Album A", shuffleGroup = "group-a")),
                track.copy(queueContext = PlaybackQueueContext("manual-B", true, "Album A")),
                track.copy(queueContext = PlaybackQueueContext("manual-C", true, "Album A")),
                track.copy(queueContext = PlaybackQueueContext("automatic", automaticShuffle = true)),
            ), currentIndex = 1, positionMs = 1234, playWhenReady = false,
            shuffleEnabled = true, shuffleOrder = listOf(0, 1, 2, 3),
        )
        assertEquals(session, parsePlaybackSession(session.toPreferenceValue()))
        assertNotEquals(session.playbackQueueIdentity(), session.copy(queue = session.queue.reversed()).playbackQueueIdentity())
        assertNotEquals(session.playbackQueueIdentity(), session.copy(shuffleOrder = listOf(2, 0, 1, 3)).playbackQueueIdentity())
    }

    @Test fun olderSessionsRemainPlainBaseQueues() {
        val old = """{"queue":[{"id":"a","uri":"file:///a.flac","title":"A","artist":"Artist"}],"currentIndex":0}"""
        val restored = requireNotNull(parsePlaybackSession(old))
        assertNull(restored.queue.single().queueContext)
        assertTrue(restored.shuffleOrder.isEmpty())
    }

    @Test fun olderQueueEntriesKeepTheirSavedResumeIdentity() {
        val old = """{"queue":[{"id":"song","uri":"file:///song.flac","title":"Song","artist":"Artist","queueEntry":"original","queueSource":"Album A"}],"currentIndex":0}"""
        assertEquals("126ff75e2fe434a4ac5826e218e6811467d4332853cde4a8c5b0a8a0338a1c6a",
            requireNotNull(parsePlaybackSession(old)).playbackQueueIdentity())
    }

    @Test fun shuffleBudgetAndHistoryAreRestoredWithTheMatchingQueueOccurrence() {
        val track = EchoTrackRef("song", "file:///song.flac", "Song", "Artist", queueContext = PlaybackQueueContext("active"))
        val session = EchoSavedPlaybackSession(listOf(track), 0, 200, false,
            shuffleResume = PlaybackShuffleResume("active", 0, listOf("old-track"), listOf("previous-entry")))
        val queue = requireNotNull(parsePlaybackSession(session.toPreferenceValue()))
        val resume = parsePlaybackResume(session.toResumePreferenceValue(session.playbackQueueIdentity()))
        assertEquals(session, queue.withPlaybackResume(resume))
    }
}
