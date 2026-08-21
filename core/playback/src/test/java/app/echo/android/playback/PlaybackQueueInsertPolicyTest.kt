package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueInsertPolicyTest {
    @Test
    fun emptyQueueReplacesInsteadOfInserting() {
        assertTrue(PlaybackQueueInsertPolicy.shouldReplaceQueue(0))
        assertFalse(PlaybackQueueInsertPolicy.shouldReplaceQueue(3))
        assertEquals(0, PlaybackQueueInsertPolicy.playNextIndex(currentIndex = 0, queueSize = 0))
    }

    @Test
    fun playNextInsertsAfterTheCurrentItem() {
        assertEquals(2, PlaybackQueueInsertPolicy.playNextIndex(currentIndex = 1, queueSize = 4))
        assertEquals(4, PlaybackQueueInsertPolicy.playNextIndex(currentIndex = 3, queueSize = 4))
        assertEquals(3, PlaybackQueueInsertPolicy.playNextIndex(currentIndex = -1, queueSize = 3))
        assertEquals(
            0,
            PlaybackQueueInsertPolicy.playNextIndex(
                currentIndex = 2,
                queueSize = 4,
                shuffledNextIndex = 0,
            ),
        )
        assertEquals(
            3,
            PlaybackQueueInsertPolicy.playNextIndex(
                currentIndex = 1,
                queueSize = 4,
                shuffledNextIndex = 3,
            ),
        )
    }

    @Test
    fun skipInsertWhenTheSameTrackIsAlreadyAtThatSlot() {
        val ids = listOf("a", "b", "c")
        assertTrue(PlaybackQueueInsertPolicy.shouldSkipInsert(ids, insertIndex = 1, trackId = "b"))
        assertFalse(PlaybackQueueInsertPolicy.shouldSkipInsert(ids, insertIndex = 1, trackId = "c"))
        assertTrue(PlaybackQueueInsertPolicy.shouldSkipEnqueue(ids, "c"))
        assertFalse(PlaybackQueueInsertPolicy.shouldSkipEnqueue(ids, "a"))
        assertTrue(PlaybackQueueInsertPolicy.shouldSkipInsert(ids, insertIndex = 0, trackId = "  "))
    }

    @Test
    fun moveIndexRejectsNoOpAndOutOfRange() {
        assertEquals(0 to 2, PlaybackQueueInsertPolicy.moveIndex(0, 2, queueSize = 3))
        assertEquals(2 to 0, PlaybackQueueInsertPolicy.moveIndex(2, 0, queueSize = 3))
        assertEquals(null, PlaybackQueueInsertPolicy.moveIndex(1, 1, queueSize = 3))
        assertEquals(null, PlaybackQueueInsertPolicy.moveIndex(0, 1, queueSize = 1))
        assertEquals(null, PlaybackQueueInsertPolicy.moveIndex(-1, 1, queueSize = 3))
        assertEquals(null, PlaybackQueueInsertPolicy.moveIndex(0, 3, queueSize = 3))
    }
}
