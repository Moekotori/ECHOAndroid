package app.echo.android.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueDragPlacementTest {
    @Test
    fun trailingMenuKeepsItsOwnLongPressAndTapTarget() {
        assertTrue(queueDragHitAllowed(303f, 352f, 48f))
        assertFalse(queueDragHitAllowed(304f, 352f, 48f))
        assertFalse(queueDragHitAllowed(330f, 352f, 48f))
        assertFalse(queueDragHitAllowed(-1f, 352f, 48f))
    }

    @Test
    fun grabbingTopOrBottomKeepsTheSameRowCenter() {
        assertEquals(130f, queueDragCenter(105f, 5f, 60), 0f)
        assertEquals(130f, queueDragCenter(155f, 55f, 60), 0f)
    }

    @Test
    fun movingDownOpensTheGapAtTheDestination() {
        val source = QueueDragTarget(7, 1, 5, 0)
        val target = QueueDragTarget(2, 4, 5, 0)
        assertEquals(listOf(0f, 0f, -62f, -62f, -62f, 0f),
            (0..5).map { queueDragDisplacement(it, 0, source, target, 62) })
        assertEquals(0f, queueDragDisplacement(3, 1, source, target, 62), 0f)
    }

    @Test
    fun movingUpAndCancellingRestoreTheOriginalPositions() {
        val source = QueueDragTarget(2, 4, 5, 1)
        val target = QueueDragTarget(7, 1, 5, 1)
        assertEquals(listOf(0f, 62f, 62f, 62f, 0f, 0f),
            (0..5).map { queueDragDisplacement(it, 1, source, target, 62) })
        assertEquals(0f, queueDragDisplacement(2, 1, null, null, 62), 0f)
    }
}
