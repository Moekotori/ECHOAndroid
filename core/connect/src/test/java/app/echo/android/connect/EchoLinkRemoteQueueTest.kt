package app.echo.android.connect

import app.echo.android.model.connect.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EchoLinkRemoteQueueTest {
    @Test
    fun liveIdentityRefreshesTheVisiblePageButProgressAndClosedSheetsDoNotFetch() = runBlocking {
        val endpoint = EchoRemoteEndpoint("pc", "PC", "127.0.0.1", 26789, "token")
        var revision = 1L
        var currentId = "occurrence-0"
        val pages = mutableListOf<Int>()
        val transport = object : EchoLinkTransport by OkHttpEchoLinkTransport() {
            override suspend fun fetchPlaybackQueue(endpoint: EchoRemoteEndpoint, page: Int): EchoRemoteQueueState {
                pages += page
                val offset = (page - 1) * 100
                return EchoRemoteQueueState(items = (offset until offset + 100).map { index ->
                    EchoRemoteTrack("same", "Occurrence $index", "Artist", null, null, 1000,
                        queueId = "occurrence-$index", queueIndex = index)
                }, totalCount = 1500, revision = revision, currentQueueId = currentId)
            }
        }
        val browser = EchoLinkRemoteQueue(this, transport, { endpoint }, { EchoRemotePlaybackQueue() }, { it.message.orEmpty() })
        try {
            browser.startWatching(); browser.refresh(); delay(10)
            browser.observe(1, "occurrence-2")
            assertEquals("occurrence-2", browser.state.value.currentQueueId)
            repeat(100) { browser.observe(1, "occurrence-2") }
            assertEquals(listOf(1), pages)
            browser.setVisibleAnchor(650)
            revision = 2; currentId = "occurrence-4"
            browser.observe(revision, currentId); delay(10)
            assertEquals(listOf(1, 7), pages)
            assertEquals(600, browser.state.value.offset)
            assertEquals("occurrence-4", browser.state.value.currentQueueId)
            browser.observe(1, "outdated")
            assertEquals("occurrence-4", browser.state.value.currentQueueId)
            browser.stopWatching()
            browser.observe(3, "closed")
            delay(10)
            assertEquals(2, pages.size)
            assertTrue(browser.state.value.items.isEmpty())
        } finally { browser.stopWatching() }
    }

    @Test
    fun pagingUsesABoundedWindowAndMovingUsesOccurrenceIdentityAndConfirmedRevision() = runBlocking {
        val endpoint = EchoRemoteEndpoint("pc", "PC", "127.0.0.1", 26789, "token")
        val commands = mutableListOf<EchoRemoteCommand>()
        var revision = 1L
        val transport = object : EchoLinkTransport by OkHttpEchoLinkTransport() {
            override suspend fun fetchPlaybackQueue(endpoint: EchoRemoteEndpoint, page: Int): EchoRemoteQueueState {
                val offset = (page - 1) * 100
                val items = (offset until minOf(offset + 100, 1500)).map { index ->
                    EchoRemoteTrack("same", "Occurrence $index", "Artist", null, null, 1000, queueId = "occurrence-$index", queueIndex = index)
                }
                return EchoRemoteQueueState(items = items, totalCount = 1500, revision = revision, currentQueueId = "occurrence-0")
            }
            override suspend fun sendCommand(endpoint: EchoRemoteEndpoint, command: EchoRemoteCommand): EchoLinkStatusResponse? {
                commands += command
                revision++
                return EchoLinkStatusResponse("PC", EchoRemotePlaybackSnapshot(queue = EchoRemotePlaybackQueue(revision = revision)))
            }
        }
        val browser = EchoLinkRemoteQueue(this, transport, { endpoint }, { EchoRemotePlaybackQueue() }, { it.message.orEmpty() })
        try {
            browser.refresh(); delay(5)
            repeat(11) { browser.loadMore(); delay(5) }
            assertEquals(1000, browser.state.value.items.size)
            assertEquals(200, browser.state.value.offset)
            browser.loadPrevious(); delay(5)
            assertEquals(100, browser.state.value.offset)
            assertEquals(1000, browser.state.value.items.size)
            browser.move("occurrence-100", 102); delay(5)
            assertEquals(EchoRemoteCommand.QueueMove("occurrence-100", 102, 1), commands.single())
            assertEquals("occurrence-100", browser.state.value.items[2].queueId)
            assertEquals(2L, browser.state.value.revision)
            assertFalse(browser.state.value.isMoving)
        } finally { browser.cancel(clear = true) }
    }
}
