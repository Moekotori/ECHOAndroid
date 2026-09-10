package app.echo.android.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.paging.compose.LazyPagingItems
import app.echo.android.feature.library.R as L10nR
import app.echo.android.model.library.AlbumSummary

internal data class LibraryAlbumEmptyCopy(
    val titleRes: Int,
    val detailRes: Int,
    val actionRes: Int,
)

internal fun libraryAlbumEmptyCopy(
    isCloud: Boolean,
    cloudConfigured: Boolean,
    hasQuery: Boolean,
): LibraryAlbumEmptyCopy = when {
    hasQuery -> LibraryAlbumEmptyCopy(
        titleRes = L10nR.string.library_no_matches,
        detailRes = L10nR.string.library_search_hint,
        actionRes = L10nR.string.library_clear_search,
    )
    isCloud && cloudConfigured -> LibraryAlbumEmptyCopy(
        titleRes = L10nR.string.library_cloud_empty_configured_title,
        detailRes = L10nR.string.library_cloud_empty_configured_detail,
        actionRes = L10nR.string.library_open_connect,
    )
    isCloud -> LibraryAlbumEmptyCopy(
        titleRes = L10nR.string.library_cloud_empty_title,
        detailRes = L10nR.string.library_cloud_empty_detail,
        actionRes = L10nR.string.library_open_connect,
    )
    else -> LibraryAlbumEmptyCopy(
        titleRes = L10nR.string.library_start_collection,
        detailRes = L10nR.string.library_import_hint,
        actionRes = L10nR.string.library_add_music,
    )
}

@Composable
internal fun GuidedAlbumWall(
    albums: LazyPagingItems<AlbumSummary>,
    onOpenAlbum: (AlbumSummary) -> Unit,
    isCloud: Boolean,
    cloudConfigured: Boolean,
    query: String,
    onClearSearch: () -> Unit,
    onAddMusic: () -> Unit,
    onOpenConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val empty = libraryAlbumEmptyCopy(
        isCloud = isCloud,
        cloudConfigured = cloudConfigured,
        hasQuery = query.isNotBlank(),
    )
    AlbumWall(
        albums = albums,
        onOpenAlbum = onOpenAlbum,
        emptyTitle = stringResource(empty.titleRes),
        emptyDetail = stringResource(empty.detailRes),
        emptyActionLabel = stringResource(empty.actionRes),
        onEmptyAction = when {
            query.isNotBlank() -> onClearSearch
            isCloud -> onOpenConnect
            else -> onAddMusic
        },
        modifier = modifier,
    )
}
