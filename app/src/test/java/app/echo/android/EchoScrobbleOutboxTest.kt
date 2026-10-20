package app.echo.android

import app.echo.android.data.PendingScrobble
import app.echo.android.data.ScrobbleOutboxStore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EchoScrobbleOutboxTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun listen() = PendingScrobble(
        "account-a", "track", "Song", "Artist", null, 180_000L, System.currentTimeMillis() / 1_000L,
    )

    @Test fun recoveredListensWaitForSettingsAndResumeAfterEnabling() = runBlocking {
        val store = ScrobbleOutboxStore(File(temporary.root, "queue.json"))
        val original = listen()
        store.enqueue(original)
        val submitted = CompletableDeferred<PendingScrobble>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val outbox = EchoScrobbleOutbox<String>(scope, store,
                submit = { _, row -> submitted.complete(row); Result.success(Unit) },
                status = { _, _, _ -> },
            )
            assertEquals(listOf(original), store.read())
            outbox.configure(ScrobbleAccount("account-a", "credential-never-persisted", false))
            assertFalse(outbox.enqueue(original))
            assertEquals(listOf(original), store.read())
            outbox.configure(ScrobbleAccount("account-a", "credential-never-persisted", true))
            assertEquals(original, withTimeout(10_000L) { submitted.await() })
            withTimeout(10_000L) { while (store.read().isNotEmpty()) delay(10L) }
            assertTrue(store.read().isEmpty())
        } finally { scope.cancel() }
    }

    @Test fun failedSubmissionSurvivesWorkerStopWithBackoff() = runBlocking {
        val file = File(temporary.root, "queue.json")
        val store = ScrobbleOutboxStore(file)
        val failed = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val outbox = EchoScrobbleOutbox<String>(scope, store,
                submit = { _, _ -> Result.failure(IOException("offline")) },
                status = { _, _, error -> if (error != null) failed.complete(Unit) },
            )
            outbox.configure(ScrobbleAccount("account-a", "credential", true))
            assertTrue(outbox.enqueue(listen()))
            withTimeout(10_000L) { failed.await() }
        } finally { scope.cancel() }
        val restored = ScrobbleOutboxStore(file).read().single()
        assertEquals(1, restored.failures)
        assertTrue(restored.retryAtEpochMs > System.currentTimeMillis())
    }
}
