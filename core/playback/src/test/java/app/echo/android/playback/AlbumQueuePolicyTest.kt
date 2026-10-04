package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumQueuePolicyTest {
    @Test fun switchingAlbumsRetainsEveryTrackOfThePreviousAlbum() {
        assertEquals(listOf(0, 1, 2), AlbumQueuePolicy.retainedIndices(
            listOf("A1", "A2", "A3"), emptySet(), listOf("B1", "B2"),
        ))
    }

    @Test fun replayingAnAlbumReplacesItsOccurrencesButKeepsOtherAlbumsAndManualRequests() {
        val ids = listOf("A1", "A2", "B1", "A1", "B2")
        val retained = AlbumQueuePolicy.retainedIndices(ids, setOf(3), listOf("A1", "A2"))
        assertEquals(listOf("B1", "A1", "B2", "A1", "A2"), retained.map(ids::get) + listOf("A1", "A2"))
    }

    @Test fun capacityKeepsTheNewestOlderTracksAndPreservesManualRequests() {
        val ids = (0..1_000).map(Int::toString)
        val retained = AlbumQueuePolicy.retainedIndices(ids, setOf(0), listOf("new1", "new2"))
        assertEquals(listOf(0) + (3..1_000), retained)
        assertEquals(emptyList<Int>(), AlbumQueuePolicy.retainedIndices(emptyList(), emptySet(), listOf("A1")))
    }

    @Test fun switchingAwayFromAManualTrackConsumesOnlyThatOccurrence() {
        assertEquals(listOf(0, 2, 3), AlbumQueuePolicy.retainedIndices(
            listOf("A1", "request1", "request2", "A2"), setOf(1, 2), listOf("B1"), currentIndex = 1,
        ))
    }
}
