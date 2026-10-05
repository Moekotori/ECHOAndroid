package app.echo.android.playback

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class PseudoRandomQueuePolicyTest {
    @Test fun artistSpacingKeepsEverySongAndCurrentTrackInPlace() {
        val artists = listOf("A", "A", "A", "B", "B", "B")
        val order = PseudoRandomQueuePolicy.order(artists.indices.toList(), 0, artists, true, Random(7))
        assertEquals(0, order.first())
        assertEquals(artists.indices.toSet(), order.toSet())
        assertEquals(artists.size, order.size)
        assertTrue(order.zipWithNext().all { (a, b) -> artists[a] != artists[b] })
    }

    @Test fun missingTagsSingleArtistAndAlreadySpacedOrdersRemainComplete() {
        val unknown = PseudoRandomQueuePolicy.spread(listOf(1, 2, 3), 0, listOf(null, "", null, " "))
        assertEquals(listOf(1, 2, 3), unknown)
        val oneArtist = PseudoRandomQueuePolicy.order(listOf(0, 1, 2), 0, listOf("A", "a", " A "), true, Random(2))
        assertEquals(setOf(0, 1, 2), oneArtist.toSet())
        assertEquals(listOf(1, 2, 3), PseudoRandomQueuePolicy.spread(listOf(1, 2, 3), 0, listOf("A", "B", "A", "B")))
    }
}
