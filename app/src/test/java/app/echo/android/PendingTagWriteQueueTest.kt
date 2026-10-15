package app.echo.android

import org.junit.Assert.*
import org.junit.Test

class PendingTagWriteQueueTest {
    private data class Request(val id: Long, val track: String)

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
}
