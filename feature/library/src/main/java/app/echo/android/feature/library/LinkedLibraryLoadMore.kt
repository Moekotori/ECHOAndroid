package app.echo.android.feature.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import app.echo.android.model.connect.EchoLinkLibraryQueryPolicy
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun LoadMoreWhenNearEnd(
    listState: LazyListState,
    itemCount: Int,
    totalCount: Int,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
) {
    val loadMore = rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState, itemCount, totalCount, isLoadingMore) {
        if (isLoadingMore || itemCount <= 0 || totalCount <= itemCount) return@LaunchedEffect
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            EchoLinkLibraryQueryPolicy.shouldRequestNextPage(
                loadedCount = itemCount,
                totalCount = totalCount,
                lastVisibleIndex = visible.lastOrNull()?.index ?: -1,
                visibleItemCount = visible.size,
                firstVisibleIndex = visible.firstOrNull()?.index ?: 0,
            )
        }.distinctUntilChanged().collect { request ->
            if (request) loadMore.value()
        }
    }
}

@Composable
internal fun LoadMoreWhenNearEnd(
    gridState: LazyGridState,
    itemCount: Int,
    totalCount: Int,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
) {
    val loadMore = rememberUpdatedState(onLoadMore)
    LaunchedEffect(gridState, itemCount, totalCount, isLoadingMore) {
        if (isLoadingMore || itemCount <= 0 || totalCount <= itemCount) return@LaunchedEffect
        snapshotFlow {
            val visible = gridState.layoutInfo.visibleItemsInfo
            EchoLinkLibraryQueryPolicy.shouldRequestNextPage(
                loadedCount = itemCount,
                totalCount = totalCount,
                lastVisibleIndex = visible.lastOrNull()?.index ?: -1,
                visibleItemCount = visible.size,
                firstVisibleIndex = visible.firstOrNull()?.index ?: 0,
            )
        }.distinctUntilChanged().collect { request ->
            if (request) loadMore.value()
        }
    }
}
