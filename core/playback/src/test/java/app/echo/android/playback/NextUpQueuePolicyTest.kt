package app.echo.android.playback

import org.junit.Assert.*
import org.junit.Test

class NextUpQueuePolicyTest {
    @Test fun appendKeepsRequestOrderWhilePlayNextPrepends() {
        assertEquals(2, NextUpQueuePolicy.insertionIndex(1, 4, emptyList(), false))
        assertEquals(3, NextUpQueuePolicy.insertionIndex(1, 5, listOf(2), false))
        assertEquals(2, NextUpQueuePolicy.insertionIndex(1, 6, listOf(2, 3), true))
        assertEquals(0, NextUpQueuePolicy.insertionIndex(-1, 0, emptyList(), false))
    }

    @Test fun manualBlockWinsOverShuffleWithoutChangingBaseTraversal() {
        // Physical: A0 A1 B C A2 A3. Original shuffle: A3 A1 A0 A2.
        val actual = NextUpQueuePolicy.shuffledOrder(listOf(5, 2, 1, 0, 3, 4), listOf(2, 3), 1)
        assertEquals(listOf(5, 1, 2, 3, 0, 4), actual)
        assertEquals(listOf(5, 1, 0, 4), actual.filterNot { it == 2 || it == 3 })
        // Calling again when the shuffle toggle changes cannot reshuffle B/C or the base.
        assertEquals(actual, NextUpQueuePolicy.shuffledOrder(actual, listOf(2, 3), 1))
    }

    @Test fun manualOnlyQueueAndEmptyManualQueueHaveDefinedTraversal() {
        assertEquals(listOf(0, 1), NextUpQueuePolicy.shuffledOrder(listOf(1, 0), listOf(0, 1), -1))
        assertEquals(listOf(2, 0, 1), NextUpQueuePolicy.shuffledOrder(listOf(2, 0, 1), emptyList(), 0))
    }

    @Test fun rejectBrokenRestoredPermutations() {
        assertTrue(NextUpQueuePolicy.validOrder(listOf(2, 0, 1), 3))
        assertFalse(NextUpQueuePolicy.validOrder(listOf(0, 0, 2), 3))
        assertFalse(NextUpQueuePolicy.validOrder(listOf(0, 1, 3), 3))
        assertFalse(NextUpQueuePolicy.validOrder(emptyList(), 3))
    }
}
