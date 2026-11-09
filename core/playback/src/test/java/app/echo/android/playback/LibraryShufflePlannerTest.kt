package app.echo.android.playback

import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibrarySource
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryShufflePlannerTest {
    @Test fun retriesTransientFailureThenWaitsForTheLibraryToChange() = runBlocking {
        val changes = MutableStateFlow(0)
        val attempts = AtomicInteger()
        val exhausted = CompletableDeferred<Unit>()
        val ready = CompletableDeferred<String>()
        val source = object : EchoPlaybackCatalog by EchoPlaybackCatalog.Empty {
            override fun localShuffleCatalogChanges() = changes.map { Unit }
            override suspend fun shuffleLocalTrack(currentId: String, recentIds: List<String>): EchoTrack? =
                when (attempts.incrementAndGet()) {
                    1 -> throw IOException("temporary failure")
                    2 -> { exhausted.complete(Unit); null }
                    else -> EchoTrack("next", "file:///next.flac", "Next", "Artist", source = LibrarySource.MediaStore)
                }
        }
        val planner = LibraryShufflePlanner(this) { source }
        try {
            planner.request("entry", "current", emptyList()) { ready.complete(it.id) }
            withTimeout(2_000) { exhausted.await() }
            planner.request("entry", "current", emptyList()) { error("duplicate draw") }
            changes.value++
            assertEquals("next", withTimeout(2_000) { ready.await() })
            assertEquals(3, attempts.get())
        } finally { planner.cancel() }
    }
}
