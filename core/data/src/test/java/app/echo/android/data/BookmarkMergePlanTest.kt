package app.echo.android.data

import app.echo.android.model.library.EchoTrack
import org.junit.Assert.*
import org.junit.Test

class BookmarkMergePlanTest {
    private fun whole() = EchoTrack("file", "content://audio/file", "Album", "Artist", durationMs = 120_000).toLibraryTrackEntity()
    private fun point(id: String, position: Long) = LibraryBookmarkEntity("$id:$position", id, position, "Moment")
    private fun clips() = whole().splitByCue(app.echo.android.model.library.CueSheet(tracks = listOf(
        app.echo.android.model.library.CueSheetTrack(1, "First", startMs = 0),
        app.echo.android.model.library.CueSheetTrack(2, "Second", startMs = 60_000),
    )))

    @Test fun restoringWholeFileOffsetsCuePointsAndRangeEndsIntoPhysicalTime() {
        val clip = clips()[1]
        val plan = bookmarkMergePlan(clip, listOf(whole()), listOf(point(clip.id, 10_000)))
        assertTrue(plan.complete)
        val move = plan.moves.single()
        assertEquals(70_000L, move.destination.positionMs)
        assertEquals(80_000L, move.endPosition(20_000))
        assertEquals(whole().contentUri, move.destination.uriSnapshot)
        assertEquals(120_000L, move.destination.durationSnapshot)
    }

    @Test fun splittingWholeFileRoutesBookmarksToTheirActualMovementIncludingExactBoundaries() {
        val targets = clips()
        val plan = bookmarkMergePlan(whole(), targets, listOf(point("file", 10_000), point("file", 60_000), point("file", 70_000)))
        assertTrue(plan.complete)
        assertEquals(listOf(targets[0].id, targets[1].id, targets[1].id), plan.moves.map { it.destination.trackId })
        assertEquals(listOf(10_000L, 0L, 10_000L), plan.moves.map { it.destination.positionMs })
        assertEquals(60_000L, plan.moves.first().endPosition(80_000))
    }

    @Test fun gapsAndOutOfRangePointsCannotBeSilentlyClampedToTheWrongTrack() {
        val targets = clips().let { listOf(it[0].copy(clipEndMs = 30_000, durationMs = 30_000), it[1]) }
        val gap = bookmarkMergePlan(whole(), targets, listOf(point("file", 45_000)))
        assertFalse(gap.complete)
        assertTrue(gap.moves.isEmpty())
        assertFalse(bookmarkMergePlan(whole(), clips(), listOf(point("file", 180_000))).complete)
        assertFalse(bookmarkMergePlan(clips()[1], listOf(whole()), listOf(point("clip", Long.MAX_VALUE))).complete)
    }
}
