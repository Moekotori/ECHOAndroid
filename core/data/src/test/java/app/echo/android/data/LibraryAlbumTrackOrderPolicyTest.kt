package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryAlbumTrackOrderPolicyTest {
    @Test
    fun reorderRenumbersTheMovedDiscOnly() {
        val tracks = listOf(
            row("a", disc = 1, number = 1),
            row("b", disc = 1, number = 2),
            row("c", disc = 1, number = 3),
            row("d", disc = 2, number = 1),
        )
        val moved = LibraryAlbumTrackOrderPolicy.reorder(tracks, fromIndex = 2, toIndex = 0)
        assertEquals(listOf("c", "a", "b", "d"), moved.map { it.id })
        assertEquals(listOf(1, 2, 3, 1), moved.map { it.trackNumber })
        assertEquals(listOf(1, 1, 1, 2), moved.map { LibraryAlbumTrackOrderPolicy.discKey(it.discNumber) })
    }

    @Test
    fun cannotMoveAcrossDiscs() {
        val tracks = listOf(
            row("a", disc = 1, number = 1),
            row("b", disc = 2, number = 1),
        )
        assertFalse(LibraryAlbumTrackOrderPolicy.canMove(tracks, 0, 1))
        assertEquals(tracks, LibraryAlbumTrackOrderPolicy.reorder(tracks, 0, 1))
    }

    @Test
    fun treatsMissingDiscAsDiscOne() {
        val tracks = listOf(
            row("a", disc = null, number = 2),
            row("b", disc = 1, number = 1),
        )
        assertTrue(LibraryAlbumTrackOrderPolicy.canMove(tracks, 0, 1))
        val moved = LibraryAlbumTrackOrderPolicy.reorder(tracks, 0, 1)
        assertEquals(listOf("b", "a"), moved.map { it.id })
        assertEquals(listOf(1, 2), moved.map { it.trackNumber })
    }

    private fun row(id: String, disc: Int?, number: Int) =
        AlbumTrackOrderRow(id = id, discNumber = disc, trackNumber = number)
}
