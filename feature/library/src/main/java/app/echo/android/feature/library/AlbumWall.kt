package app.echo.android.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import app.echo.android.model.library.AlbumSummary
import kotlinx.coroutines.CancellationException
import app.echo.android.feature.library.R as L10nR

@Composable
internal fun AlbumWall(
    albums: LazyPagingItems<AlbumSummary>,
    onOpenAlbum: (AlbumSummary) -> Unit,
    emptyTitle: String = stringResource(L10nR.string.feature_library_this_library_has_no_albums_to_show_yet_925b12),
    emptyDetail: String? = null,
    emptyActionLabel: String? = null,
    onEmptyAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    findLetterIndex: (suspend (Char) -> Int)? = null,
) {
    if (albums.isInitialPagingLoad()) {
        LibraryCollectionEmpty(stringResource(L10nR.string.feature_library_loading_albums_75c4f3))
        return
    }
    if (albums.isInitialPagingError()) {
        LibraryCollectionEmpty(stringResource(L10nR.string.feature_library_failed_to_load_albums_8a8685))
        return
    }
    if (albums.itemCount == 0) {
        LibraryCollectionEmpty(
            title = emptyTitle,
            detail = emptyDetail,
            actionLabel = emptyActionLabel,
            onAction = onEmptyAction,
        )
        return
    }
    val gridState = rememberLazyGridState()
    val snackbar = remember { SnackbarHostState() }
    val failed = stringResource(L10nR.string.feature_library_library_query_failed_f9c538)
    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = LibraryWallGridCells,
            modifier = Modifier.fillMaxSize().padding(end = if (findLetterIndex != null) 28.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(LibraryWallHorizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(LibraryWallVerticalSpacing),
            contentPadding = PaddingValues(bottom = LibraryBottomControlsPadding),
        ) {
            items(
                count = albums.itemCount,
                key = albums.itemKey { it.albumKey },
            ) { index: Int ->
                val album = albums[index]
                if (album != null) {
                    AlbumWallCard(album = album, onClick = { onOpenAlbum(album) })
                } else {
                    // Unloaded rows need a size so distant letter jumps can land before loading finishes.
                    Column {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow))
                        Spacer(Modifier.height(64.dp))
                    }
                }
            }
        }
        if (findLetterIndex != null) {
            // A refresh replaces the ordering; cancel any lookup against the previous generation.
            key(albums.loadState.refresh) {
                LibraryAlphabetIndex(modifier = Modifier.align(Alignment.CenterEnd), onJump = { letter ->
                    try {
                        val index = findLetterIndex(letter)
                        if (albums.itemCount > 0) {
                            val target = index.coerceIn(0, albums.itemCount - 1)
                            albums[target] // Request the page even when the destination is a placeholder.
                            gridState.scrollToItem(target)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        snackbar.showSnackbar(failed)
                    }
                })
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = LibraryBottomControlsPadding))
    }
}
