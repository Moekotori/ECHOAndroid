package app.echo.android.model.settings

import org.junit.Assert.*
import org.junit.Test

class EchoPlayerMenuLayoutTest {
    @Test fun repairsMissingAndDuplicateActionsWithoutLosingHiddenChoices() {
        val layout = EchoPlayerMenuLayout(listOf(EchoPlayerMenuAction.Info, EchoPlayerMenuAction.Info), setOf(EchoPlayerMenuAction.Info)).normalized()
        assertEquals(EchoPlayerMenuAction.Info, layout.order.first())
        assertEquals(EchoPlayerMenuAction.entries.toSet(), layout.order.toSet())
        assertEquals(EchoPlayerMenuAction.entries.size, layout.order.size)
        assertTrue(EchoPlayerMenuAction.Info in layout.hidden)
    }

    @Test fun movingPreservesVisibilityAndStopsAtListEdges() {
        val layout = EchoPlayerMenuLayout(hidden = EchoPlayerMenuAction.entries.toSet())
        val moved = layout.moved(EchoPlayerMenuAction.Info, -100)
        assertEquals(EchoPlayerMenuAction.Info, moved.order.first())
        assertEquals(layout.hidden, moved.hidden)
        assertEquals(moved, moved.moved(EchoPlayerMenuAction.Info, -1))
    }
}
