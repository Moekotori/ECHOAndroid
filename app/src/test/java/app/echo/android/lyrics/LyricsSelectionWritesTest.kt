package app.echo.android.lyrics

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class LyricsSelectionWritesTest {
    @Test
    fun cancelledBlockingReaderCannotOverwriteNewImport() = runBlocking {
        val writes = LyricsSelectionWrites()
        val oldVersion = writes.begin("song")
        val started = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val saved = AtomicReference("original")
        val old = launch(Dispatchers.IO) {
            started.countDown()
            check(unblock.await(5, TimeUnit.SECONDS))
            writes.runIfCurrent("song", oldVersion) { saved.set("old") }
        }
        try {
            withContext(Dispatchers.IO) { check(started.await(5, TimeUnit.SECONDS)) }
            old.cancel()
            val latest = writes.begin("song")
            writes.runIfCurrent("song", latest) { saved.set("new") }
        } finally {
            unblock.countDown()
            old.join()
        }
        assertEquals("new", saved.get())
    }

    @Test
    fun replacementAndClearInvalidateQueuedWritesOnlyForTheirTrack() = runBlocking {
        val writes = LyricsSelectionWrites()
        val pending = writes.begin("song")
        val other = writes.begin("other")
        writes.begin("song") // A new import, edit or clear invalidates the previous request.
        var staleWriteRan = false
        assertFalse(writes.runIfCurrent("song", pending) { staleWriteRan = true })
        assertFalse(staleWriteRan)
        assertTrue(writes.runIfCurrent("other", other) {})
    }

    @Test
    fun commitAlreadyInProgressFinishesBeforeLatestCommit() = runBlocking {
        val writes = LyricsSelectionWrites()
        val first = writes.begin("song")
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val old = launch {
            writes.runIfCurrent("song", first) {
                entered.complete(Unit)
                finish.await()
                order += "old"
            }
        }
        entered.await()
        val latest = writes.begin("song")
        val new = launch { writes.runIfCurrent("song", latest) { order += "new" } }
        finish.complete(Unit)
        old.join()
        new.join()
        assertEquals(listOf("old", "new"), order)
    }
}
