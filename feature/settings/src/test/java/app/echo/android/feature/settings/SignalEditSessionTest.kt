package app.echo.android.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalEditSessionTest {
    @Test fun undoRestoresTheGestureStartRatherThanItsLastPreview() {
        val saved = mutableListOf<Int>()
        val session = SignalEditSession<Int>({}, saved::add)
        session.preview(2, before = 1)
        session.previewPending()
        session.preview(3, before = 2)
        assertFalse(session.canUndo(3))
        session.finish()
        assertTrue(session.canUndo(3))
        session.undo(3)
        session.undo(3)
        assertEquals(listOf(3, 1), saved)
        session.previewPending()
        assertFalse(session.canUndo(1))
    }

    @Test fun returningToStartDoesNotSaveOrErasePreviousUndo() {
        val heard = mutableListOf<Int>()
        val saved = mutableListOf<Int>()
        val session = SignalEditSession<Int>(heard::add, saved::add)
        session.commit(2, before = 1)
        session.preview(3, before = 2)
        session.previewPending()
        session.preview(2, before = 3)
        session.finish()
        assertEquals(listOf(3, 2), heard)
        assertEquals(listOf(2), saved)
        assertTrue(session.canUndo(2))
        session.undo(2)
        assertEquals(listOf(2, 1), saved)
    }

    @Test fun externalChangesCannotBeOverwrittenByUndo() {
        val saved = mutableListOf<Int>()
        val session = SignalEditSession<Int>({}, saved::add)
        session.commit(2, before = 1)
        assertFalse(session.canUndo(7))
        session.undo(7)
        assertEquals(listOf(2), saved)
    }

    @Test fun dragCoalescesAndOnlyPersistsTheFinalValue() {
        val heard = mutableListOf<Int>()
        val saved = mutableListOf<Int>()
        val session = SignalEditSession<Int>(heard::add, saved::add)
        session.preview(1)
        session.preview(2)
        session.previewPending()
        session.previewPending()
        assertEquals(listOf(2), heard)
        assertEquals(emptyList<Int>(), saved)
        session.preview(3)
        session.commit(4)
        session.previewPending() // An already queued preview cannot overwrite a finished edit.
        session.finish()
        assertEquals(listOf(2), heard)
        assertEquals(listOf(4), saved)
    }

    @Test fun leavingDuringDragSavesTheLatestValueOnce() {
        val saved = mutableListOf<Int>()
        val session = SignalEditSession<Int>({}, saved::add)
        session.preview(10)
        session.preview(20)
        session.finish()
        session.finish()
        session.previewPending()
        assertEquals(listOf(20), saved)
    }
}
