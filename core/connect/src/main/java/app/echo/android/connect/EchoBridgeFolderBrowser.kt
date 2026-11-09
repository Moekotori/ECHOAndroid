package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.connect.EchoRemoteLibraryState
import app.echo.android.model.library.FolderSortMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Owns one directory page and a bounded back trail for opaque peer folder IDs. */
internal class EchoBridgeFolderBrowser(
    private val scope: CoroutineScope,
    private val transport: EchoLinkTransport,
    private val endpoint: () -> EchoRemoteEndpoint?,
    private val state: MutableStateFlow<EchoRemoteLibraryState>,
    private val errorMessage: (Throwable) -> String,
) {
    private data class Parent(val path: String, val page: Int, val query: String, val sort: FolderSortMode)
    private val parents = ArrayDeque<Parent>()
    private var job: Job? = null
    private var generation = 0L
    private var active = false
    private var query = ""
    private var sort = FolderSortMode.Path

    fun reset() {
        generation++; job?.cancel(); job = null
        parents.clear(); active = false; query = ""; sort = FolderSortMode.Path
    }

    fun refresh(path: String) {
        val current = state.value
        val previousParents = parents.toList()
        var page = if (path == current.folderPath && current.query == query) current.folderPage else 1
        if (path != current.folderPath) {
            val ancestor = parents.indexOfLast { it.path == path }
            if (ancestor >= 0) {
                val parent = parents[ancestor]
                page = if (parent.query == current.query && parent.sort == sort) parent.page else 1
                while (parents.size > ancestor) parents.removeLast()
            } else if (path.isEmpty()) parents.clear()
            else if (active) {
                if (parents.size == 128) parents.removeFirst()
                parents.addLast(Parent(current.folderPath, current.folderPage, query, sort))
            }
        }
        load(path, page, current.query, previousParents)
    }

    fun changePage(page: Int) {
        val current = state.value
        if (current.loadingFolderPath != null || page !in 1..current.folderPageCount || page == current.folderPage) return
        load(current.folderPath, page, query)
    }

    fun search(value: String) {
        if (active && !state.value.foldersUnavailable && query != value) load(state.value.folderPath, 1, value)
    }

    fun changeSort(value: FolderSortMode) {
        if (sort == value) return
        sort = value
        if (active && !state.value.foldersUnavailable) load(state.value.folderPath, 1, query)
    }

    private fun load(path: String, page: Int, search: String, previousParents: List<Parent> = emptyList()) {
        val target = endpoint() ?: return
        val previous = state.value
        val request = ++generation
        val requestedSort = sort
        job?.cancel(); active = true; query = search
        state.update { it.copy(folderPath = path, folderPage = page,
            folderPageCount = if (it.folderPath == path) it.folderPageCount else 1,
            folderParentPath = parents.lastOrNull()?.path ?: "", folders = emptyList(), folderTracks = emptyList(),
            loadingFolderPath = path, foldersUnavailable = false, error = null) }
        job = scope.launch {
            try {
                val result = transport.fetchFolderPage(target, path, page, search, requestedSort)
                if (request != generation || !EchoLinkRequestPolicy.isSameEndpoint(endpoint(), target)) return@launch
                val count = if (result.pageSize > 0) {
                    val total = maxOf(result.folderTotalCount, result.trackTotalCount)
                    (total - 1).coerceAtLeast(0) / result.pageSize + 1
                } else 1
                state.update { it.copy(folders = result.folders, folderTracks = result.tracks,
                    folderPath = result.path.ifBlank { path },
                    folderParentPath = it.folderParentPath.takeIf { result.pageSize > 0 },
                    folderPageCount = maxOf(page, count), foldersUnavailable = false, loadingFolderPath = null, error = null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (request != generation || !EchoLinkRequestPolicy.isSameEndpoint(endpoint(), target)) return@launch
                val notFound = (error as? EchoLinkHttpException)?.statusCode == 404
                val unavailable = !target.libraryOnly && EchoLinkRequestPolicy.shouldMarkFoldersUnavailable(path, notFound)
                val restoreLegacy = notFound && previous.folderParentPath == null && previous.folderPath != path
                if (restoreLegacy) {
                    parents.clear()
                    parents.addAll(previousParents)
                }
                state.update {
                    if (restoreLegacy) it.copy(
                        folderPath = previous.folderPath, folders = previous.folders, folderTracks = previous.folderTracks,
                        folderParentPath = previous.folderParentPath, folderPage = previous.folderPage,
                        folderPageCount = previous.folderPageCount, loadingFolderPath = null, error = errorMessage(error),
                    ) else it.copy(
                        loadingFolderPath = null,
                        foldersUnavailable = unavailable,
                        error = if (unavailable) it.error else errorMessage(error),
                    )
                }
            }
        }
    }
}
