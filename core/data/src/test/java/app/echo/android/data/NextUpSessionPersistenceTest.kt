package app.echo.android.data

import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.playback.PlaybackQueueContext
import org.junit.Assert.*
import org.junit.Test

class NextUpSessionPersistenceTest {
    @Test fun preserveDuplicateRequestsSourceAndResumeOrder() {
        val track = EchoTrackRef("song", "file:///song.flac", "Song", "Artist")
        val session = EchoSavedPlaybackSession(
            queue = listOf(
                track.copy(queueContext = PlaybackQueueContext("original", source = "Album A")),
                track.copy(queueContext = PlaybackQueueContext("manual-B", true, "Album A")),
                track.copy(queueContext = PlaybackQueueContext("manual-C", true, "Album A")),
            ), currentIndex = 1, positionMs = 1234, playWhenReady = false,
            shuffleEnabled = true, shuffleOrder = listOf(0, 1, 2),
        )
        assertEquals(session, parsePlaybackSession(session.toPreferenceValue()))
        assertNotEquals(session.playbackQueueIdentity(), session.copy(queue = session.queue.reversed()).playbackQueueIdentity())
        assertNotEquals(session.playbackQueueIdentity(), session.copy(shuffleOrder = listOf(2, 0, 1)).playbackQueueIdentity())
    }

    @Test fun olderSessionsRemainPlainBaseQueues() {
        val old = """{"queue":[{"id":"a","uri":"file:///a.flac","title":"A","artist":"Artist"}],"currentIndex":0}"""
        val restored = requireNotNull(parsePlaybackSession(old))
        assertNull(restored.queue.single().queueContext)
        assertTrue(restored.shuffleOrder.isEmpty())
    }
}
