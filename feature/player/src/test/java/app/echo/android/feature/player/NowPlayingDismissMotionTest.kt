package app.echo.android.feature.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NowPlayingDismissMotionTest {
    private fun connection(state: NowPlayingDismissDragState, enabled: Boolean = true, settle: (Float) -> Unit = {}) =
        NowPlayingDismissConnection(state, { enabled }, { 100f }, {}, settle)

    @Test fun slowPullAccumulatesSmallDeltas() {
        val state = NowPlayingDismissDragState()
        val connection = connection(state)
        repeat(6) { connection.onPostScroll(Offset.Zero, Offset(0f, 2f), NestedScrollSource.UserInput) }
        assertEquals(12f, state.offsetPx, 0.001f)
    }

    @Test fun lyricsAutoScrollCannotDragPlayer() {
        val state = NowPlayingDismissDragState()
        val connection = connection(state)
        assertEquals(Offset.Zero, connection.onPostScroll(Offset.Zero, Offset(0f, 50f), NestedScrollSource.SideEffect))
        assertEquals(0f, state.offsetPx, 0f)
        state.offsetPx = 20f
        assertEquals(Offset.Zero, connection.onPreScroll(Offset(0f, -10f), NestedScrollSource.SideEffect))
        assertEquals(20f, state.offsetPx, 0f)
    }

    @Test fun newDragCancelsOldSpringWithoutJumping() {
        val state = NowPlayingDismissDragState()
        val settling = Job()
        state.offsetPx = 40f
        state.settleJob = settling
        state.applyDelta(-5f, 100f) {}
        assertTrue(settling.isCancelled)
        assertEquals(35f, state.offsetPx, 0f)
    }

    @Test fun reversingPullReturnsUnusedScrollToChild() {
        val state = NowPlayingDismissDragState()
        state.offsetPx = 12f
        val consumed = connection(state).onPreScroll(Offset(25f, -30f), NestedScrollSource.UserInput)
        assertEquals(Offset(0f, -12f), consumed)
        assertEquals(0f, state.offsetPx, 0f)
    }

    @Test fun horizontalFlingRemainsAvailableToPager() = runBlocking {
        val state = NowPlayingDismissDragState()
        var velocity: Float? = null
        val connection = connection(state) { velocity = it }
        assertEquals(Velocity.Zero, connection.onPostFling(Velocity.Zero, Velocity(900f, 0f)))
        assertNull(velocity)
        state.offsetPx = 40f
        assertEquals(Velocity(0f, 100f), connection.onPostFling(Velocity.Zero, Velocity(900f, 100f)))
        assertEquals(100f, velocity!!, 0f)
    }

    @Test fun committedDismissKeepsFingerPositionForExit() = runBlocking {
        val state = NowPlayingDismissDragState()
        state.offsetPx = 120f
        var dismissed = false
        settleNowPlayingDismiss(state, 0f, 100f, 1000f) { dismissed = true }
        assertTrue(dismissed)
        assertEquals(120f, state.offsetPx, 0f)
    }

    @Test fun blockingSheetPreventsPull() {
        val state = NowPlayingDismissDragState()
        assertEquals(Offset.Zero, connection(state, enabled = false).onPostScroll(
            Offset.Zero, Offset(0f, 50f), NestedScrollSource.UserInput,
        ))
        assertEquals(0f, state.offsetPx, 0f)
    }
}
