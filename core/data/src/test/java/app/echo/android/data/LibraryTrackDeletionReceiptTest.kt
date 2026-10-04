package app.echo.android.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class LibraryTrackDeletionReceiptTest {
    @Test
    fun cancelledPageStillReconcilesAlreadyDeletedFiles() = runBlocking {
        val deleted = setOf("file:///music/deleted.flac")
        val started = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val job = launch {
            try { started.complete(Unit); awaitCancellation() }
            finally {
                finalizeDeletedFiles(deleted, { assertEquals(deleted, it); events.add("queue") }) {
                    events.add("index")
                }
            }
        }
        started.await()
        job.cancelAndJoin()
        assertEquals(listOf("index", "queue"), events)
    }

    @Test
    fun indexFailureDoesNotLeaveDeletedFilesInQueueAndEmptyReceiptDoesNothing() = runBlocking {
        val notifications = mutableListOf<Set<String>>()
        val deleted = setOf("content://media/external/audio/media/1")
        try {
            finalizeDeletedFiles(deleted, { notifications.add(it) }) { throw IOException("index failed") }
        } catch (_: IOException) { }
        assertEquals(listOf(deleted), notifications)
        finalizeDeletedFiles(emptySet(), { error("No file was deleted") }) { error("No file was deleted") }
        assertTrue(notifications.size == 1)
    }
}
