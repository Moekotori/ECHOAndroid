package app.echo.android.feature.library

import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems

internal fun LazyPagingItems<*>.isInitialPagingLoad(): Boolean =
    itemCount == 0 && loadState.refresh is LoadState.Loading

internal fun LazyPagingItems<*>.isInitialPagingError(): Boolean =
    itemCount == 0 && loadState.refresh is LoadState.Error
