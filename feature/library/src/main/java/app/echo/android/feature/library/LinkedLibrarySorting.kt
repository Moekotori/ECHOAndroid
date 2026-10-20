package app.echo.android.feature.library

import app.echo.android.model.library.AlbumSortMode
import app.echo.android.model.library.LibraryTrackSortMode

internal val LinkedTrackSortOptions = listOf(
    LibraryTrackSortMode.Title, LibraryTrackSortMode.Artist,
    LibraryTrackSortMode.Album, LibraryTrackSortMode.Duration,
    LibraryTrackSortMode.Random,
)
internal val LinkedAlbumSortOptions = listOf(
    AlbumSortMode.Title, AlbumSortMode.Artist, AlbumSortMode.TrackCount, AlbumSortMode.Duration,
)
internal fun linkedTrackSort(mode: LibraryTrackSortMode): LibraryTrackSortMode =
    mode.takeIf { it in LinkedTrackSortOptions } ?: LibraryTrackSortMode.Title
internal fun linkedAlbumSort(mode: AlbumSortMode): AlbumSortMode =
    mode.takeIf { it in LinkedAlbumSortOptions } ?: AlbumSortMode.Title
