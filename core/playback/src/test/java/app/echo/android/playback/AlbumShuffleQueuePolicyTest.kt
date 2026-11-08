package app.echo.android.playback

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class AlbumShuffleQueuePolicyTest {
    private fun entry(index: Int, album: String?, track: Int, disc: Int = 1, artist: String = "artist", source: String = "local") =
        AlbumShuffleEntry(index, album, artist, artist, source, disc, track)

    @Test fun shufflesAlbumsAndPreservesMultidiscTrackOrderAndHistory() {
        val entries = listOf(entry(0, "history", 1), entry(1, "A", 2), entry(2, "A", 1),
            entry(3, "B", 2), entry(4, "B", 1, 2), entry(5, "B", 1), entry(6, "C", 1))
        val order = AlbumShuffleQueuePolicy.order(entries.indices.toList(), 1, entries, Random(7))
        assertEquals(entries.indices.toSet(), order.toSet())
        assertEquals(listOf(0, 2, 1), order.take(3)) // Current A2 is preserved; A1 is before it.
        val b = order.filter { it in 3..5 }
        assertEquals(listOf(5, 3, 4), b)
        assertEquals(listOf(0, 1, 2), b.map { order.indexOf(it) - order.indexOf(b.first()) })
    }

    @Test fun freshShuffleStartsAtFirstTrackOfAnAlbumAndDoesNotMergeMissingOrDifferentOwners() {
        val entries = listOf(entry(0, "same", 2), entry(1, "same", 1),
            entry(2, "same", 1, artist = "other"), entry(3, "same", 1, source = "remote"),
            entry(4, null, 9), entry(5, null, 1))
        assertEquals(5, entries.groupBy { it.key }.size)
        repeat(12) { assertTrue(AlbumShuffleQueuePolicy.firstOfRandomAlbum(entries, Random(it)) in setOf(1, 2, 3, 4, 5)) }
    }

    @Test fun albumArtistKeepsCompilationTogether() {
        val entries = listOf(AlbumShuffleEntry(0, "mix", "A", "Various Artists", "local", 1, 2),
            AlbumShuffleEntry(1, "mix", "B", "Various Artists", "local", 1, 1))
        assertEquals(entries[0].key, entries[1].key)
        assertEquals(1, AlbumShuffleQueuePolicy.firstOfRandomAlbum(entries, Random(1)))
    }

    @Test fun movingUpcomingUsesPlaybackOrderAndNeverSelectsCurrentOrHistory() {
        val ids = listOf("same", "current", "last", "same")
        assertEquals(3, upcomingQueueMatch(listOf(0, 1, 2, 3), 1, ids, "same"))
        assertNull(upcomingQueueMatch(listOf(3, 0, 1, 2), 1, ids, "same"))
        assertEquals(0, upcomingQueueMatch(listOf(3, 1, 0, 2), 1, ids, "same"))
        assertNull(upcomingQueueMatch(listOf(0, 1, 2), 0, listOf("same", "a", "b"), "same"))
    }
}
