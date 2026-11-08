package app.echo.android.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One bounded metadata snapshot per paging generation; read failures become retryable load errors. */
internal class FilePlaylistPagingSource(private val readRows: suspend () -> List<LibraryTrackEntity>,
    private val observe: (FilePlaylistPagingSource) -> Unit = {}) : PagingSource<Int, LibraryTrackEntity>() {
    private var snapshot: List<LibraryTrackEntity>? = null
    private val reads = Mutex()
    private var observing = false
    override fun getRefreshKey(state: PagingState<Int, LibraryTrackEntity>): Int? =
        state.anchorPosition?.let { (it - state.config.initialLoadSize / 2).coerceAtLeast(0) }
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, LibraryTrackEntity> = withContext(Dispatchers.IO) {
        reads.withLock {
            try {
                if (invalid) return@withLock LoadResult.Invalid()
                if (!observing) { observing = true; observe(this@FilePlaylistPagingSource) }
                val rows = snapshot ?: readRows().also { snapshot = it }
                if (invalid) return@withLock LoadResult.Invalid()
                val boundary = (params.key ?: 0).coerceIn(0, rows.size)
                val from = if (params is LoadParams.Prepend) (boundary - params.loadSize).coerceAtLeast(0) else boundary
                val to = if (params is LoadParams.Prepend) boundary else (from + params.loadSize).coerceAtMost(rows.size)
                LoadResult.Page(rows.subList(from, to), if (from == 0) null else from,
                    if (to == rows.size) null else to, itemsBefore = from, itemsAfter = rows.size - to)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { LoadResult.Error(error) }
        }
    }
}
