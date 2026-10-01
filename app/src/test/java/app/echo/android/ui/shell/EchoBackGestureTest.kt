package app.echo.android.ui.shell

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EchoBackGestureTest {
    @Test fun completedGestureCommitsOnce() = runBlocking {
        val updates = mutableListOf<Float>()
        var commits = 0
        collectEchoBackGesture(flowOf(-1f, 0.4f, 2f), { true }, updates::add,
            { fail("Completed gesture was cancelled") }, { commits++ })
        assertEquals(listOf(0f, 0.4f, 1f), updates)
        assertEquals(1, commits)
    }

    @Test fun cancellationRestoresAndPropagatesWithoutCommitting() = runBlocking {
        var restored = false
        val cancelled = CancellationException("Gesture cancelled")
        try {
            collectEchoBackGesture(flow { emit(0.4f); throw cancelled }, { true }, {},
                { restored = true }, { fail("Cancelled gesture committed") })
            fail("Cancellation was swallowed")
        } catch (actual: CancellationException) { assertSame(cancelled, actual) }
        assertTrue(restored)
    }

    @Test fun losingOwnershipCannotCommitOrWriteTheNewOwner() = runBlocking {
        var active = true
        val updates = mutableListOf<Float>()
        collectEchoBackGesture(flow { emit(0.2f); active = false; emit(0.6f) }, { active },
            updates::add, { fail("Stale owner restored") }, { fail("Stale owner committed") })
        assertEquals(listOf(0.2f), updates)
    }

    @Test fun staleEnabledCallbackDoesNotEvenCollect() = runBlocking {
        collectEchoBackGesture(flow { fail("Inactive owner collected"); emit(1f) }, { false }, {},
            { fail("Inactive owner restored") }, { fail("Inactive owner committed") })
    }
}
