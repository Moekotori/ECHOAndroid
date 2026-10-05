package app.echo.android.feature.library

import app.echo.android.model.library.EchoTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBatchSelectionTest {
    @Test
    fun singleTrackDeletionShowsFeedbackWithoutEnteringSelectionMode() {
        val selection = LibraryBatchSelection()
        selection.beginDeletion(setOf("1"))
        assertEquals(setOf("1"), selection.deletingIds)
        assertTrue(selection.locked)
        assertFalse(selection.active)
        assertTrue(selection.selected.isEmpty())
        selection.start(track(2))
        assertFalse(selection.active)
        selection.finishDeletion()
        assertTrue(selection.deletingIds.isEmpty())
        assertFalse(selection.locked)
    }

    @Test
    fun retainedOffscreenSelectionDoesNotOwnBack() {
        val selection = LibraryBatchSelection()
        selection.start(track(1))
        assertTrue(selection.ownsBack(pageInteractive = true, modalOpen = false))
        assertFalse(selection.ownsBack(pageInteractive = false, modalOpen = false))
        assertTrue(selection.active)
        assertEquals(listOf("1"), selection.tracks().map(EchoTrack::id))
        assertTrue(selection.ownsBack(pageInteractive = true, modalOpen = false))
    }

    @Test
    fun childDialogsAndInactiveModeLeaveBackToTheirOwner() {
        val selection = LibraryBatchSelection()
        assertFalse(selection.ownsBack(pageInteractive = true, modalOpen = false))
        selection.start(track(1))
        assertFalse(selection.ownsBack(pageInteractive = true, modalOpen = true))
    }

    @Test
    fun deletingFeedbackLocksOnlySelectedTargetsAndRestoresAfterCancellation() {
        val selection = LibraryBatchSelection()
        selection.start(track(1))
        selection.toggle(track(2))
        selection.beginDeletion(setOf("1", "other"))
        assertEquals(setOf("1"), selection.deletingIds)
        assertTrue(selection.locked)
        selection.toggle(track(2))
        assertEquals(listOf("1", "2"), selection.tracks().map(EchoTrack::id))
        selection.finishDeletion()
        assertTrue(selection.deletingIds.isEmpty())
        assertFalse(selection.locked)
        assertEquals(listOf("1", "2"), selection.tracks().map(EchoTrack::id))
    }

    @Test
    fun partialDeletionRetainsFailedTracksInSelectionOrder() {
        val selection = LibraryBatchSelection()
        selection.start(track(1))
        selection.toggle(track(2))
        selection.toggle(track(3))
        selection.remove(setOf("2"))
        assertTrue(selection.active)
        assertEquals(listOf("1", "3"), selection.tracks().map(EchoTrack::id))
        selection.locked = true
        selection.remove(setOf("1"))
        assertEquals(listOf("1", "3"), selection.tracks().map(EchoTrack::id))
    }

    @Test
    fun deselectingLastTrackKeepsSelectionModeUntilExit() {
        val selection = LibraryBatchSelection()
        val track = track(1)
        selection.start(track)
        selection.toggle(track)
        assertTrue(selection.active)
        assertTrue(selection.selected.isEmpty())
        selection.toggle(track)
        assertEquals(listOf(track.id), selection.selected.keys.toList())
        selection.exit()
        assertFalse(selection.active)
        assertTrue(selection.selected.isEmpty())
    }

    @Test
    fun startingAgainPreservesSelectionAndClearKeepsMode() {
        val selection = LibraryBatchSelection()
        selection.start(track(1))
        selection.start(track(1))
        selection.start(track(2))
        assertEquals(2, selection.selected.size)
        selection.clear()
        assertTrue(selection.active)
        assertTrue(selection.selected.isEmpty())
    }

    @Test
    fun limitAllowsDeselectingAndReplacingTracksWithoutChangingOrder() {
        val selection = LibraryBatchSelection()
        selection.start(track(0))
        (1..LibraryBatchSelection.Limit).forEach { selection.toggle(track(it)) }
        assertEquals(LibraryBatchSelection.Limit, selection.selected.size)
        assertFalse(track(LibraryBatchSelection.Limit).id in selection.selected)
        selection.toggle(track(0))
        selection.toggle(track(LibraryBatchSelection.Limit))
        assertEquals(LibraryBatchSelection.Limit, selection.selected.size)
        assertEquals("1", selection.tracks().first().id)
        assertEquals(LibraryBatchSelection.Limit.toString(), selection.tracks().last().id)
    }

    @Test
    fun lockedSelectionRejectsEditsAndInactiveTogglesDoNothing() {
        val selection = LibraryBatchSelection()
        selection.toggle(track(1))
        assertFalse(selection.active)
        assertTrue(selection.selected.isEmpty())
        selection.start(track(1))
        selection.locked = true
        selection.toggle(track(1))
        selection.start(track(2))
        selection.clear()
        selection.exit()
        assertTrue(selection.active)
        assertEquals(listOf("1"), selection.selected.keys.toList())
    }

    private fun track(index: Int) = EchoTrack(
        id = index.toString(), uri = "content://tracks/$index", title = "Track $index", artist = "Artist",
    )
}
