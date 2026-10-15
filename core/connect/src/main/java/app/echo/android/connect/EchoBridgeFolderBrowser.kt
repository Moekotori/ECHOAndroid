package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.connect.EchoRemoteLibraryState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Owns one directory page and a bounded back trail; opaque Bridge IDs are never parsed as paths. */
internal class EchoBridgeFolderBrowser(
    private val scope: CoroutineScope,
    private val transport: EchoLinkTransport,
    private val endpoint: () -> EchoRemoteEndpoint?,
    private val state: MutableStateFlow<EchoRemoteLibraryState>,
    private val errorMessage: (Throwable) -> String,
) {
    private data class Parent(val path: String, val page: Int, val query: String)
    private val parents = ArrayDeque<Parent>()
    private var job: Job? = null
    private var generation = 0L
    private var active = false
    private var query = ""

    fun reset() {
        generation++; job?.cancel(); job = null
        parents.clear(); active = false; query = ""
    }

    fun refresh(path: String) {
        val current = state.value
        var page = if (path == current.folderPath && current.query == query) current.folderPage else 1
        if (path != current.folderPath) {
            val ancestor = parents.indexOfLast { it.path == path }
            if (ancestor >= 0) {
                val parent = parents[ancestor]
                page = if (parent.query == current.query) parent.page else 1
                while (parents.size > ancestor) parents.removeLast()
            } else if (path.isEmpty()) parents.clear()
            else if (active) {
                if (parents.size == 128) parents.removeFirst()
                parents.addLast(Parent(current.folderPath, current.folderPage, query))
            }
        }
        load(path, page, current.query)
    }

    fun changePage(page: Int) {
        val current = state.value
        if (current.loadingFolderPath != null || page !in 1..current.folderPageCount || page == current.folderPage) return
        load(current.folderPath, page, query)
    }

    fun search(value: String) {
        if (active && query != value) load(state.value.folderPath, 1, value)
    }

    private fun load(path: String, page: Int, search: String) {
        val target = endpoint() ?: return
        val request = ++generation
        job?.cancel(); active = true; query = search
        state.update { it.copy(folderPath = path, folderPage = page,
            folderPageCount = if (it.folderPath == path) it.folderPageCount else 1,
            folderParentPath = parents.lastOrNull()?.path ?: "", folders = emptyList(), folderTracks = emptyList(),
            loadingFolderPath = path, error = null) }
        job = scope.launch {
            try {
                val result = transport.fetchFolderPage(target, path, page, search)
                if (request != generation || !EchoLinkRequestPolicy.isSameEndpoint(endpoint(), target)) return@launch
                val total = maxOf(result.folderTotalCount, result.trackTotalCount)
                val count = (total - 1).coerceAtLeast(0) / result.pageSize + 1
                state.update { it.copy(folders = result.folders, folderTracks = result.tracks,
                    folderPageCount = maxOf(page, count), foldersUnavailable = false, loadingFolderPath = null, error = null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (request == generation && EchoLinkRequestPolicy.isSameEndpoint(endpoint(), target))
                    state.update { it.copy(loadingFolderPath = null, error = errorMessage(error)) }
            }
        }
    }
}
