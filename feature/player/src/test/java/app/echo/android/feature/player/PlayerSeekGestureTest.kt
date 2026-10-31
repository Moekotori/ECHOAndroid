package app.echo.android.feature.player

import org.junit.Assert.*
import org.junit.Test

class PlayerSeekGestureTest {
    private class Session {
        val previews = mutableListOf<Float>()
        val commits = mutableListOf<Float>()
        var starts = 0
        var cancels = 0
        val gesture = PlayerSeekGesture(20f, 20f, 10f,
            { playerSeekFraction(it, 100f, 0f, false) },
            { starts++ }, previews::add, commits::add, { cancels++ })
    }

    @Test fun tapWaitsForReleaseAndCommitsOnce() {
        val s = Session()
        assertFalse(s.gesture.move(23f, 22f))
        assertTrue(s.commits.isEmpty())
        assertTrue(s.gesture.release(25f, 22f, true))
        s.gesture.cancel()
        s.gesture.release(90f, 22f, true)
        assertEquals(listOf(0.25f), s.commits)
        assertEquals(0, s.starts)
        assertEquals(0, s.cancels)
    }

    @Test fun horizontalDragPreviewsWithoutSeekingAndUsesTheUpPosition() {
        val s = Session()
        assertTrue(s.gesture.move(40f, 21f))
        assertTrue(s.gesture.move(60f, 80f)) // Direction stays locked after acquisition.
        assertTrue(s.commits.isEmpty())
        assertTrue(s.gesture.release(110f, 80f, false))
        assertEquals(listOf(0.4f, 0.6f, 1f), s.previews)
        assertEquals(listOf(1f), s.commits)
        assertEquals(1, s.starts)
    }

    @Test fun verticalOrEqualDiagonalMovementCannotBecomeSeekLater() {
        for ((x, y) in listOf(22f to 40f, 30f to 30f)) {
            val s = Session()
            assertFalse(s.gesture.move(x, y))
            assertFalse(s.gesture.move(90f, 21f))
            assertFalse(s.gesture.release(90f, 21f, true))
            assertTrue(s.previews.isEmpty())
            assertTrue(s.commits.isEmpty())
            assertEquals(1, s.cancels)
        }
    }

    @Test fun cancellationClearsPreviewWithoutCommittingAndIsIdempotent() {
        val s = Session()
        s.gesture.move(50f, 20f)
        s.gesture.cancel() // Multi-touch, parent interception, key change, or disposal.
        s.gesture.cancel()
        assertFalse(s.gesture.release(80f, 20f, true))
        assertEquals(1, s.cancels)
        assertTrue(s.commits.isEmpty())
    }

    @Test fun releaseOnlyMovementStillRespectsDirectionAndTapBounds() {
        val vertical = Session()
        assertFalse(vertical.gesture.release(21f, 50f, true))
        assertTrue(vertical.commits.isEmpty())
        val horizontal = Session()
        assertTrue(horizontal.gesture.release(80f, 22f, true))
        assertEquals(listOf(0.8f), horizontal.commits)
        val outside = Session()
        assertFalse(outside.gesture.release(21f, 20f, false))
        assertTrue(outside.commits.isEmpty())
    }

    @Test fun insetAndRtlMatchTheRailAndClampOutsidePositions() {
        assertEquals(0f, playerSeekFraction(-50f, 100f, 12f, false), 0f)
        assertEquals(1f, playerSeekFraction(120f, 100f, 12f, false), 0f)
        assertEquals(0.5f, playerSeekFraction(50f, 100f, 12f, false), 0f)
        assertEquals(1f, playerSeekFraction(12f, 100f, 12f, true), 0f)
        assertEquals(0f, playerSeekFraction(88f, 100f, 12f, true), 0f)
        assertTrue(playerSeekFraction(0f, 0f, 12f, false).isFinite())
        assertEquals(0f, playerSeekFraction(4f, 8f, 12f, false), 0f)
    }
}
