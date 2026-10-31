package app.echo.android

import org.junit.Assert.*
import org.junit.Test

class PendingTagWriteQueueTest {
    private data class Request(val id: Long, val track: String)

    @Test
    fun cancelledBatchRequestCanBeRemovedWithoutConsumingAnotherRequest() {
        val queue = PendingTagWriteQueue<Request> { it.id }
        queue.enqueue(Request(1, "single"))
        queue.enqueue(Request(2, "cancelled batch"))
        queue.enqueue(Request(3, "next"))
        queue.remove(2)
        assertEquals(1L, queue.pending.value?.id)
        assertNull(queue.take(2))
        queue.take(1)
        assertEquals(3L, queue.pending.value?.id)
        queue.remove(3)
        assertNull(queue.pending.value)
    }

    @Test
    fun callbacksCannotConsumeAnotherTracksAuthorization() {
        val queue = PendingTagWriteQueue<Request> { it.id }
        val first = Request(1, "A")
        val second = Request(2, "B")
        assertTrue(queue.enqueue(first))
        assertTrue(queue.enqueue(second))
        assertEquals(first, queue.pending.value)
        assertNull(queue.take(2))
        assertEquals(first, queue.pending.value)
        assertEquals(first, queue.take(1))
        assertEquals(second, queue.pending.value)
        assertNull(queue.take(1))
        assertEquals(second, queue.take(2))
        assertNull(queue.pending.value)
    }

    @Test
    fun overflowNeverReplacesAnOutstandingRequest() {
        val queue = PendingTagWriteQueue<Request>(capacity = 1) { it.id }
        val first = Request(1, "A")
        assertTrue(queue.enqueue(first))
        assertFalse(queue.enqueue(Request(2, "B")))
        assertEquals(first, queue.pending.value)
    }

    @Test
    fun restoredLaunchedIdWithoutPendingIsCleared() {
        assertEquals(TagWriteConsentLaunchDecision.Idle, tagWriteConsentLaunchDecision(pendingRequestId = null, launchedRequestId = null))
        assertEquals(TagWriteConsentLaunchDecision.ClearLaunchedId, tagWriteConsentLaunchDecision(pendingRequestId = null, launchedRequestId = 7L))
    }

    @Test
    fun staleLaunchedIdNeverSuppressesTheNextDialog() {
        assertEquals(TagWriteConsentLaunchDecision.DialogInFlight, tagWriteConsentLaunchDecision(pendingRequestId = 7L, launchedRequestId = 7L))
        assertEquals(TagWriteConsentLaunchDecision.LaunchDialog, tagWriteConsentLaunchDecision(pendingRequestId = 7L, launchedRequestId = null))
        assertEquals(TagWriteConsentLaunchDecision.LaunchDialog, tagWriteConsentLaunchDecision(pendingRequestId = 8L, launchedRequestId = 7L))
    }
}
