package app.echo.android.model.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class NextUpQueueStateTest {
    @Test fun splitSectionsFollowPlaybackOrderAndKeepDuplicateOccurrences() {
        val song = EchoTrackRef("song", "file:///song.flac", "Song", "Artist")
        val items = (0..4).map { song.copy(queueContext = PlaybackQueueContext("entry-$it", it == 1 || it == 2, "Album A")) }
        val state = PlaybackQueueState(items, 1, listOf(4, 0, 1, 2, 3))
        assertEquals(listOf(2), state.nextUpIndices)
        assertEquals(listOf(3), state.continuationIndices)
        assertEquals(listOf(3, 4, 0), state.copy(repeatAll = true).continuationIndices)
        assertEquals("Album A", state.source)
        assertEquals(5, state.items.map { it.queueContext?.entryId }.toSet().size)
    }
}
