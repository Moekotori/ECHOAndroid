package app.echo.android.playback

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopShufflePolicyTest {
    private val policy = DesktopShufflePolicy(Random(7))
    private val keys = listOf("a", "b", "c", "d")
    private val ids = listOf("A", "B", "C", "D")
    private val tags = listOf(
        ShuffleTrackTags("Artist", "Album", "Artist"),
        ShuffleTrackTags(" artist ", "Other album", "Artist"),
        ShuffleTrackTags("Guest", "ALBUM", "Artist"),
        ShuffleTrackTags("Different", "New album", null),
    )

    @Test fun balancedDrawAvoidsSameArtistAndAlbumAndKeepsThePreparedNextDuringInsertion() {
        policy.observe("a", "A", manual = false)
        val next = policy.next(keys.indices.toList(), keys, ids, 0, 25, tags::get)
        assertEquals(3, next)
        val order = DesktopShufflePolicy.order(keys.indices.toList(), 0, setOf(1, 2), next)
        assertEquals(listOf(0, 1, 2, 3), order)
        policy.observe("b", "B", manual = true)
        assertEquals(next, policy.next(listOf(0, 3), keys, ids, 1, 25, tags::get))
        assertEquals(listOf(0, 1, 2, 3), DesktopShufflePolicy.order(order, 1, setOf(1, 2), next))
    }

    @Test fun lastPlayOfEachSongControlsFallbackAndHistoryIsBounded() {
        listOf("b", "c", "b", "d", "a").forEach { key ->
            policy.observe(key, ids[keys.indexOf(key)], manual = false)
        }
        assertEquals(2, policy.next(keys.indices.toList(), keys, ids, 0, 100, tags::get))
        assertNull(policy.next(listOf(0), keys, ids, 0, 100, tags::get))
        repeat(120) { policy.observe("entry-$it", "track-$it", manual = false) }
        assertEquals((19..118).map { "track-$it" }, policy.recent(100))
    }

    @Test fun restoringALockedNextTakesPrecedenceOverANewDraw() {
        policy.observe("a", "A", manual = false)
        policy.preferNext("b")
        assertEquals(1, policy.next(keys.indices.toList(), keys, ids, 0, 25, tags::get))
        policy.observe("b", "B", manual = false)
        assertEquals(3, policy.next(listOf(0, 3), keys, ids, 1, 25, tags::get))
    }

    @Test fun scopedShuffleShowsTheRemainingQueueAfterTheLockedNext() {
        assertEquals(listOf(1, 2, 3, 0), DesktopShufflePolicy.order(
            existing = listOf(1, 2, 0, 3), current = 2, manual = emptySet(), next = 3, keepUpcoming = true,
        ))
    }
}
