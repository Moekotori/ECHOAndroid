package app.echo.android.model.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoHomeLayoutTest {
    @Test fun normalizationKeepsExistingOrderAndAddsMissingSectionsOnce() {
        val layout = EchoHomeLayout(
            order = listOf(EchoHomeSection.Artists, EchoHomeSection.Recent, EchoHomeSection.Artists),
            hidden = setOf(EchoHomeSection.Artists),
        ).normalized()
        assertEquals(listOf(EchoHomeSection.Artists, EchoHomeSection.Recent), layout.order.take(2))
        assertEquals(EchoHomeSection.entries.toSet(), layout.order.toSet())
        assertEquals(EchoHomeSection.entries.size, layout.order.size)
        assertEquals(setOf(EchoHomeSection.Artists), layout.hidden)
    }

    @Test fun hidingAndRestoringASectionKeepsItsPosition() {
        val original = EchoHomeLayout().move(EchoHomeSection.Artists, -1)
        val hidden = original.withVisibility(EchoHomeSection.Artists, false)
        assertTrue(EchoHomeSection.Artists in hidden.hidden)
        assertEquals(original.order, hidden.order)
        val visible = hidden.withVisibility(EchoHomeSection.Artists, true)
        assertFalse(EchoHomeSection.Artists in visible.hidden)
        assertEquals(original, visible)
    }

    @Test fun movingAtEitherBoundaryDoesNotWrapOrLoseSections() {
        val original = EchoHomeLayout()
        assertEquals(original, original.move(original.order.first(), -1))
        assertEquals(original, original.move(original.order.last(), 1))
        val moved = original.move(EchoHomeSection.Recent, -1)
        assertEquals(EchoHomeSection.Recent, moved.order.first())
        assertEquals(original.order.toSet(), moved.order.toSet())
    }
}
