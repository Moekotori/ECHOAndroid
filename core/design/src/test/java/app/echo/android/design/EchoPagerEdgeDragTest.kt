package app.echo.android.design

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class EchoPagerEdgeDragTest {
    @Test fun unconsumedOutwardScrollReachesAdjacentPage() {
        val edge = EchoPagerEdgeDrag()
        edge.begin(0)
        repeat(3) { edge.add(Offset.Zero, Offset(20f, 0f), 3) }
        assertEquals(-1, edge.destination(0, 3, 48f))
        edge.begin(2)
        edge.add(Offset.Zero, Offset(-60f, 0f), 3)
        assertEquals(1, edge.destination(2, 3, 48f))
    }
    @Test fun childControlAndVerticalScrollCannotLeaveThePage() {
        val edge = EchoPagerEdgeDrag()
        edge.begin(0)
        edge.add(Offset(30f, 0f), Offset(60f, 0f), 3)
        assertEquals(0, edge.destination(0, 3, 48f))
        edge.add(Offset.Zero, Offset(60f, 0f), 3)
        edge.add(Offset(0f, 30f), Offset(2f, 0f), 3)
        assertEquals(0, edge.destination(0, 3, 48f))
    }
    @Test fun reversedOrCancelledGestureCannotCommit() {
        val edge = EchoPagerEdgeDrag()
        edge.begin(0)
        edge.add(Offset.Zero, Offset(60f, 0f), 3)
        edge.add(Offset.Zero, Offset(-10f, 0f), 3)
        assertEquals(0, edge.destination(0, 3, 48f))
        edge.add(Offset.Zero, Offset(60f, 0f), 3)
        edge.reset()
        assertEquals(0, edge.destination(0, 3, 48f))
    }
    @Test fun innerPageChangeAndInteriorPageCannotNavigateOutward() {
        val edge = EchoPagerEdgeDrag()
        edge.begin(0)
        edge.add(Offset.Zero, Offset(60f, 0f), 3)
        assertEquals(0, edge.destination(1, 3, 48f))
        edge.begin(1)
        edge.add(Offset.Zero, Offset(100f, 0f), 3)
        assertEquals(0, edge.destination(1, 3, 48f))
    }
}
