package app.echo.android.playback

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoLinkStreamResolverTest {
    private val mediaId = "echo-link:track-1"
    private val rejected = "http://pc:26789/echo-link/media/old"

    @Test
    fun failedRefreshDoesNotReturnTheRejectedUrl() = runBlocking {
        for (result in listOf(null, "", "echo-link://track/track-1")) {
            val failure = runCatching {
                resolveEchoLinkStreamUri(mediaId, rejected, EchoPlaybackStreamResolver { _, _ -> result })
            }.exceptionOrNull()
            assertTrue(failure is IOException)
        }
    }

    @Test
    fun successfulRefreshReturnsTheNewUrl() = runBlocking {
        val fresh = "http://pc:26789/echo-link/media/new"
        assertEquals(fresh, resolveEchoLinkStreamUri(mediaId, rejected,
            EchoPlaybackStreamResolver { _, _ -> fresh }))
        assertEquals("content://music/1", resolveEchoLinkStreamUri("local-1", "content://music/1",
            EchoPlaybackStreamResolver { _, _ -> error("local audio must not resolve") }))
    }

    @Test
    fun timeoutCancelsTheUnderlyingResolve() = runBlocking {
        var cancelled = false
        val failure = runCatching {
            resolveEchoLinkStreamUri(mediaId, rejected, EchoPlaybackStreamResolver { _, _ ->
                try { awaitCancellation() } finally { cancelled = true }
            }, timeoutMs = 50L)
        }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertTrue(cancelled)
    }

    @Test
    fun callerCancellationIsNotConvertedToAnIoFailure() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val pending = async {
            resolveEchoLinkStreamUri(mediaId, rejected, EchoPlaybackStreamResolver { _, _ ->
                started.complete(Unit)
                awaitCancellation()
            })
        }
        started.await()
        pending.cancel()
        pending.join()
        assertTrue(pending.isCancelled)
    }
}
