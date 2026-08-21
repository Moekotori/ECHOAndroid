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
    }
}
