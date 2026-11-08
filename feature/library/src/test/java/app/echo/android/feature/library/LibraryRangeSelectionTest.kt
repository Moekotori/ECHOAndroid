package app.echo.android.feature.library

import app.echo.android.model.library.EchoTrack
import org.junit.Assert.*
import org.junit.Test

class LibraryRangeSelectionTest {
    private fun tracks(count: Int) = List(count) { EchoTrack("$it", "content://$it", "$it", "artist") }
    private fun source(rows: List<EchoTrack?>, token: Any = Any()) = LibraryRangeSource(token, { rows.size }, rows::get)

    @Test fun reverseRangeUsesDisplayOrderAndKeepsExistingSelectionWithinTheLimit() {
        val source = source(tracks(10))
        val result = resolveLibraryRange(source, LibraryRangeAnchor(source.token, 7, "7"), "3", setOf("9"))
        assertEquals(listOf("3", "4", "5", "6", "7"), (result as LibraryRangeResult.Ready).tracks.map { it.id })
    }

    @Test fun sortingFilteringOrDroppingTheAnchorRejectsTheWholeRange() {
        val source = source(tracks(10))
        val anchor = LibraryRangeAnchor(source.token, 3, "3")
        assertEquals(LibraryRangeResult.Changed, resolveLibraryRange(source(tracks(10)), anchor, "7", emptySet()))
        assertEquals(LibraryRangeResult.Changed, resolveLibraryRange(source(tracks(10).reversed(), source.token), anchor, "7", emptySet()))
        assertEquals(LibraryRangeResult.Changed, resolveLibraryRange(source(tracks(2), source.token), anchor, "1", emptySet()))
    }

    @Test fun anUnloadedPageDoesNotReturnAPartialSelection() {
        val rows = tracks(10).map { it as EchoTrack? }.toMutableList().apply { this[5] = null }
        val source = source(rows)
        assertEquals(LibraryRangeResult.Unloaded, resolveLibraryRange(source, LibraryRangeAnchor(source.token, 3, "3"), "7", emptySet()))
        rows[5] = tracks(10)[5]
        assertTrue(resolveLibraryRange(source, LibraryRangeAnchor(source.token, 3, "3"), "7", emptySet()) is LibraryRangeResult.Ready)
    }

    @Test fun aRefreshCannotSilentlyReplaceSongsInsideTheChosenRange() {
        val rows = tracks(10).toMutableList()
        val source = source(rows)
        val known = (1..5).associateWith { rows[it].id }
        val anchor = LibraryRangeAnchor(source.token, 1, "1", known)
        rows[3] = EchoTrack("replacement", "content://new", "new", "artist")
        assertEquals(LibraryRangeResult.Changed, resolveLibraryRange(source, anchor, "5", emptySet()))
    }

    @Test fun totalSelectionLimitIsAtomicAndDuplicateIdsAreRejected() {
        val source = source(tracks(201)); val anchor = LibraryRangeAnchor(source.token, 0, "0")
        assertEquals(LibraryRangeResult.TooLarge, resolveLibraryRange(source, anchor, "200", emptySet()))
        assertEquals(LibraryRangeResult.TooLarge, resolveLibraryRange(source, anchor, "199", setOf("outside")))
        assertTrue(resolveLibraryRange(source, anchor, "199", setOf("0")) is LibraryRangeResult.Ready)
        val duplicates = source(listOf(tracks(1)[0], tracks(1)[0]))
        assertEquals(LibraryRangeResult.Changed, resolveLibraryRange(duplicates, LibraryRangeAnchor(duplicates.token, 1, "0"), "0", emptySet()))
    }
}
