package app.echo.android.feature.library

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TrackMetadataSaveStateTest {
    @Test fun failureKeepsEditorOpenAndSuccessCanBeConsumedByTheCurrentHost() = runBlocking {
        val state = TrackMetadataSaveState()
        assertFalse(state.save { false })
        assertFalse(state.saving)
        assertFalse(state.completed)
        assertTrue(state.save { true })
        assertFalse(state.saving)
        assertTrue(state.completed)
        state.consumeCompletion()
        assertFalse(state.completed)
    }

    @Test fun retainedOperationRejectsDuplicateSubmissionWhileWaitingAndClosing() = runBlocking {
        val state = TrackMetadataSaveState()
        val response = CompletableDeferred<Boolean>()
        val operation = async(start = CoroutineStart.UNDISPATCHED) { state.save { response.await() } }
        assertTrue(state.saving)
        var duplicateRan = false
        assertFalse(state.save { duplicateRan = true; true })
        assertFalse(duplicateRan)
        response.complete(true)
        assertTrue(operation.await())
        assertTrue(state.completed)
        assertFalse(state.save { duplicateRan = true; true })
        assertFalse(duplicateRan)
    }
}
