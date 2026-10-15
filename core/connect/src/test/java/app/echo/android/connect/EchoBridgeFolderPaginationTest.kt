package app.echo.android.connect

import app.echo.android.model.connect.*
import app.echo.android.model.library.FolderSortMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Protect traversal beyond 500 entries without retaining earlier directory pages. */
class EchoBridgeFolderPaginationTest {
    private val endpoint = EchoRemoteEndpoint("bridge", "Bridge", "127.0.0.1", 49234, "", libraryOnly = true)

    @Test fun transportReadsOnlyRequestedPageForRootsAndMixedDirectoryContents() = runBlocking {
        val requests = mutableListOf<Request>()
        val transport = EchoBridgeTransport { request ->
            requests += request
            assertEquals("11", request.url.queryParameter("page"))
            assertEquals("find", request.url.queryParameter("q"))
            val songs = request.url.queryParameter("kind") == "songs"
            assertEquals(if (songs) null else "sizeDesc", request.url.queryParameter("sort"))
            JSONObject().put("catalog", JSONObject().put("page", 11).put("pageSize", 50).put("total", if (songs) 521 else 520)
                .put("entries", JSONArray().apply { repeat(if (songs) 21 else 20) { i ->
                    put(JSONObject().put("id", "item-${501 + i}").put("title", "Item").put("canPlay", true))
                } }))
        }
        val root = transport.folders(endpoint, "", 11, "find", FolderSortMode.Size)
        assertEquals(1, requests.size); assertEquals(20, root.folders.size); assertTrue(root.tracks.isEmpty())
        val child = transport.folders(endpoint, "node:opaque", 11, "find", FolderSortMode.Size)
        assertEquals(3, requests.size); assertEquals(20, child.folders.size); assertEquals(21, child.tracks.size)
        assertEquals(520, child.folderTotalCount); assertEquals(521, child.trackTotalCount)
        assertEquals("node:opaque", requests.last().url.queryParameter("parentId"))
    }

    @Test fun pageReplacementAndOpaqueParentNavigationRestoreTheParentPage() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = MutableStateFlow(EchoRemoteLibraryState())
        val browser = browser(scope, state) { path, page, _ -> samplePage(path, page) }
        try {
            browser.refresh(""); browser.changePage(11)
            assertEquals(11, state.value.folderPage)
            assertEquals(listOf("11"), state.value.folders.map { it.path })
            browser.refresh("root:music"); browser.changePage(12)
            browser.refresh("node:opaque-child")
            assertEquals("root:music", state.value.folderParentPath)
            browser.refresh("root:music")
            assertEquals(12, state.value.folderPage)
            browser.refresh("")
            assertEquals(11, state.value.folderPage)
            assertEquals(1, state.value.folders.size)
        } finally { browser.reset(); scope.cancel() }
    }

    @Test fun changedDirectoryAndSearchDiscardLateResultsAndResetThePage() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = MutableStateFlow(EchoRemoteLibraryState())
        val late = CompletableDeferred<EchoLinkFolderPage>()
        val requests = mutableListOf<Triple<String, Int, String>>()
        val browser = browser(scope, state) { path, page, query ->
            requests += Triple(path, page, query)
            if (path == "node:slow") withContext(NonCancellable) { late.await() } else samplePage(path, page)
        }
        try {
            browser.refresh("node:slow"); browser.refresh("root:current"); browser.changePage(12)
            state.update { it.copy(query = "search") }; browser.search("search")
            late.complete(samplePage("node:slow", 1)); yield()
            assertEquals("root:current", state.value.folderPath); assertEquals(1, state.value.folderPage)
            assertEquals(Triple("root:current", 1, "search"), requests.last())
            assertNull(state.value.loadingFolderPath)
            browser.reset()
        } finally { late.complete(samplePage("node:slow", 1)); browser.reset(); scope.cancel() }
    }

    @Test fun failedPageCanBeRetriedWithoutKeepingStaleRows() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = MutableStateFlow(EchoRemoteLibraryState())
        var failed = false
        val browser = browser(scope, state) { path, page, _ ->
            if (page == 2 && !failed) { failed = true; throw java.io.IOException("Page failed") }
            samplePage(path, page)
        }
        try {
            browser.refresh("root:music"); browser.changePage(2)
            assertTrue(state.value.folders.isEmpty()); assertEquals("Page failed", state.value.error)
            browser.refresh("root:music")
            assertEquals(2, state.value.folderPage); assertEquals("2", state.value.folders.single().path)
            assertNull(state.value.error)
        } finally { browser.reset(); scope.cancel() }
    }

    @Test fun changedSortResetsPageDiscardsLateResultsAndInvalidatesSavedParentPage() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = MutableStateFlow(EchoRemoteLibraryState())
        val late = CompletableDeferred<EchoLinkFolderPage>()
        val requests = mutableListOf<Pair<Int, FolderSortMode>>()
        val transport = object : EchoLinkTransport by OkHttpEchoLinkTransport() {
            override suspend fun fetchFolderPage(endpoint: EchoRemoteEndpoint, path: String, page: Int,
                query: String, sort: FolderSortMode): EchoLinkFolderPage {
                requests += page to sort
                return if (sort == FolderSortMode.TrackCount) withContext(NonCancellable) { late.await() }
                    else samplePage(path, page)
            }
        }
        val browser = EchoBridgeFolderBrowser(scope, transport, { endpoint }, state) { it.message.orEmpty() }
        try {
            browser.refresh(""); browser.changePage(11); browser.refresh("root:music")
            browser.changeSort(FolderSortMode.TrackCount)
            browser.changeSort(FolderSortMode.Duration)
            late.complete(samplePage("old", 12)); yield()
            assertEquals(1, state.value.folderPage)
            assertEquals("root:music", state.value.folderPath)
            assertEquals(listOf("1"), state.value.folders.map { it.path })
            browser.refresh("")
            assertEquals(1 to FolderSortMode.Duration, requests.last())
            browser.changePage(2)
            assertEquals(2 to FolderSortMode.Duration, requests.last())
        } finally { late.complete(samplePage("old", 12)); browser.reset(); scope.cancel() }
    }

    private fun samplePage(path: String, page: Int) = EchoLinkFolderPage(path,
        listOf(EchoRemoteFolder(page.toString(), "Folder")), emptyList(), page, 50, 1051, 0)

    private fun browser(scope: CoroutineScope, state: MutableStateFlow<EchoRemoteLibraryState>,
        fetch: suspend (String, Int, String) -> EchoLinkFolderPage): EchoBridgeFolderBrowser =
        EchoBridgeFolderBrowser(scope, object : EchoLinkTransport by OkHttpEchoLinkTransport() {
            override suspend fun fetchFolderPage(endpoint: EchoRemoteEndpoint, path: String, page: Int, query: String,
                sort: FolderSortMode) = fetch(path, page, query)
        }, { endpoint }, state) { it.message.orEmpty() }
}
