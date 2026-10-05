package app.echo.android.playback

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class PureRandomQueuePolicyTest {
    private val keys = listOf("a", "b", "c", "request")
    private val ids = listOf("A", "B", "C", "D")
    private val firstCandidate = object : Random() {
        override fun nextBits(bitCount: Int) = 0
    }

    @Test fun drawsCanRevisitPlayedSongsButNeverTheCurrentSong() {
        val policy = PureRandomQueuePolicy(firstCandidate)
        var order = policy.order(listOf(0, 1, 2), keys.take(3), ids.take(3), 0, emptySet(), true)
        assertEquals(1, order[order.indexOf(0) + 1])
        order = policy.order(order, keys.take(3), ids.take(3), 1, emptySet(), true)
        assertEquals(0, order[order.indexOf(1) + 1])
        assertTrue(NextUpQueuePolicy.validOrder(order, 3))
    }

    @Test fun timelineRefreshKeepsTheSamePreparedDraw() {
        val policy = PureRandomQueuePolicy(Random(42))
        val order = policy.order(listOf(0, 1, 2), keys.take(3), ids.take(3), 1, emptySet(), true)
        repeat(5) { assertEquals(order, policy.order(order, keys.take(3), ids.take(3), 1, emptySet(), true)) }
        policy.preferNext("c")
        val edited = policy.order(order, keys.take(3), ids.take(3), 1, emptySet(), true)
        assertEquals(2, edited[edited.indexOf(1) + 1])
    }

    @Test fun requestsStayAheadAndDoNotConsumeTheBaseRound() {
        val policy = PureRandomQueuePolicy(firstCandidate)
        var order = policy.order(listOf(0, 3, 1, 2), keys, ids, 0, setOf(3), false)
        assertEquals(listOf(3, 1), order.drop(order.indexOf(0) + 1).take(2))
        order = policy.order(order, keys, ids, 3, setOf(3), false)
        assertEquals(1, order[order.indexOf(3) + 1])
        // The queue controller removes consumed manual entries before resuming the base queue.
        order = policy.order(order.filter { it != 3 }, keys.take(3), ids.take(3), 1, emptySet(), false)
        assertEquals(0, order[order.indexOf(1) + 1])
        order = policy.order(order, keys.take(3), ids.take(3), 0, emptySet(), false)
        assertEquals(0, order.last())
    }

    @Test fun repeatOffStopsAfterPlaylistSizedRoundAndResetStartsANewRound() {
        val policy = PureRandomQueuePolicy(firstCandidate)
        var order = policy.order(listOf(0, 1, 2), keys.take(3), ids.take(3), 0, emptySet(), false)
        order = policy.order(order, keys.take(3), ids.take(3), 1, emptySet(), false)
        order = policy.order(order, keys.take(3), ids.take(3), 0, emptySet(), false)
        assertEquals(0, order.last())
        policy.reset()
        order = policy.order(order, keys.take(3), ids.take(3), 0, emptySet(), false)
        assertNotEquals(0, order.last())
    }

    @Test fun duplicateSongEntriesAndSingleSongDoNotProduceImmediateDuplicates() {
        val policy = PureRandomQueuePolicy(firstCandidate)
        val order = policy.order(listOf(0, 1, 2), keys.take(3), listOf("A", "A", "B"), 0, emptySet(), true)
        assertEquals(2, order[order.indexOf(0) + 1])
        assertEquals(listOf(0), policy.order(listOf(0), keys.take(1), ids.take(1), 0, emptySet(), false))
    }

    @Test fun recentAvoidanceRelaxesOldestSongsInSmallQueuesWithoutChangingTheRound() {
        val policy = PureRandomQueuePolicy(firstCandidate)
        var order = policy.order(listOf(0, 1, 2), keys.take(3), ids.take(3), 0, emptySet(), true, 20)
        order = policy.order(order, keys.take(3), ids.take(3), 1, emptySet(), true, 20)
        assertEquals(2, order[order.indexOf(1) + 1]) // A is still excluded.
        order = policy.order(order, keys.take(3), ids.take(3), 2, emptySet(), true, 20)
        assertEquals(0, order[order.indexOf(2) + 1]) // Release A before the more recent B.
        assertEquals(order, policy.order(order, keys.take(3), ids.take(3), 2, emptySet(), true, 20))
    }

    @Test fun changingAvoidanceReplacesOnlyAnIneligiblePreparedDraw() {
        val policy = PureRandomQueuePolicy(firstCandidate)
        var order = policy.order(listOf(0, 1, 2), keys.take(3), ids.take(3), 0, emptySet(), false)
        order = policy.order(order, keys.take(3), ids.take(3), 1, emptySet(), false)
        assertEquals(0, order[order.indexOf(1) + 1])
        order = policy.order(order, keys.take(3), ids.take(3), 1, emptySet(), false, 5)
        assertEquals(2, order[order.indexOf(1) + 1])
        order = policy.order(order, keys.take(3), ids.take(3), 2, emptySet(), false, 5)
        assertEquals(2, order.last()) // A settings change did not restart the round.
    }
}
