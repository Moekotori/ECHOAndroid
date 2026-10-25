package app.echo.android

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.echo.android.connect.EchoRemoteClient
import app.echo.android.feature.home.UnifiedSearchItem
import kotlinx.coroutines.CancellationException

internal class PcSearchPagingSource(private val pc: EchoRemoteClient, private val query: String,
    private val endpoint: Any?) : PagingSource<Int, UnifiedSearchItem>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UnifiedSearchItem> = try {
        check(pc.status.value.endpoint == endpoint)
        val page = params.key ?: 1
        // A fixed wire page size keeps keys stable when Paging changes loadSize.
        val (tracks, total) = pc.searchTracksPage(query, page, 30)
        check(pc.status.value.endpoint == endpoint)
        LoadResult.Page(tracks.map { UnifiedSearchItem.PcTrack(it) },
            prevKey = if (page > 1) page - 1 else null,
            nextKey = if (tracks.isNotEmpty() && page.toLong() * 30 < total) page + 1 else null)
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { LoadResult.Error(error) }

    override fun getRefreshKey(state: PagingState<Int, UnifiedSearchItem>): Int? = state.anchorPosition?.let { anchor ->
        state.closestPageToPosition(anchor)?.let { it.prevKey?.plus(1) ?: it.nextKey?.minus(1) }
    }
}
