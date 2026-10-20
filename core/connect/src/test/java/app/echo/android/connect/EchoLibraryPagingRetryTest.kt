package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteEndpoint
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class EchoLibraryPagingRetryTest {
    private val endpoint = EchoRemoteEndpoint("test", "PC", "192.168.1.20", 26789, "token")

    @Test fun failedPageRetriesTheSameCursorWithoutDroppingLoadedTracks() = verifyRetry(emptyPage = false)

    @Test fun prematureEmptyPageIsReportedAndCanBeRetried() = verifyRetry(emptyPage = true)

    private fun verifyRetry(emptyPage: Boolean) = runBlocking {
        val base = FakeEchoLinkTransport(libraryPageSize = 2, libraryTotalCount = 4)
        val requestedPages = mutableListOf<Int>()
        var failNextPage = true
        val transport = object : EchoLinkTransport by base {
            override suspend fun fetchTracks(endpoint: EchoRemoteEndpoint, query: String, page: Int, pageSize: Int): EchoLinkTrackPage {
                requestedPages.add(page)
                if (page == 2 && failNextPage) {
                    failNextPage = false
                    if (emptyPage) return EchoLinkTrackPage(emptyList(), 4)
                    throw IOException("temporary failure")
                }
                return base.fetchTracks(endpoint, query, page, pageSize)
            }
        }
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        try {
            client.connect(endpoint, refreshLibraryOnConnect = true)
            val firstPage = withTimeout(2_000) { client.library.first { it.tracks.size == 2 } }
            client.loadMoreTracks()
            val failed = withTimeout(2_000) { client.library.first { !it.isLoadingMore && it.error != null } }
            assertEquals(firstPage.tracks, failed.tracks)
            assertEquals(listOf(1, 2), requestedPages)
            client.loadMoreTracks()
            val completed = withTimeout(2_000) { client.library.first { it.tracks.size == 4 } }
            assertEquals(listOf(1, 2, 2), requestedPages)
            assertEquals(firstPage.tracks, completed.tracks.take(2))
            assertEquals(4, completed.tracks.map { it.id }.distinct().size)
            assertNull(completed.error)
            assertFalse(completed.isLoadingMore)
        } finally {
            client.disconnect()
        }
    }
}
