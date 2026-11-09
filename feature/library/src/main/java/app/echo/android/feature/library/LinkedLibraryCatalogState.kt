package app.echo.android.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import app.echo.android.model.connect.EchoRemoteLibraryState
import app.echo.android.model.library.AlbumSortMode
import app.echo.android.model.library.ArtistSortMode
import app.echo.android.model.library.LibraryTrackSortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val MaxLocalSearchPreviewTracks = 500

@Composable
internal fun rememberLinkedLibraryCatalog(
    mode: LinkedLibraryMode,
    state: EchoRemoteLibraryState,
    query: String,
    remoteQuery: String,
    trackSortMode: LibraryTrackSortMode,
    albumSortMode: AlbumSortMode,
    artistSortMode: ArtistSortMode,
    searchText: LinkedLibrarySearchTextCache,
    randomOrder: LinkedLibraryRandomOrder,
): LinkedLibraryCatalog {
    if (mode == LinkedLibraryMode.Folders) return LinkedLibraryCatalog.Empty

    val trackResultQuery = state.trackResultQuery
    val waitingForTracks = query.isNotEmpty() && (query != remoteQuery || state.isLoading) && query != trackResultQuery
    val tracks = when (mode) {
        LinkedLibraryMode.Songs, LinkedLibraryMode.Artists -> state.tracks
        LinkedLibraryMode.Albums -> state.tracks.takeIf { state.albums.isEmpty() }.orEmpty()
        else -> emptyList()
    }.takeUnless { waitingForTracks && it.size > MaxLocalSearchPreviewTracks }.orEmpty()
    val albums = state.albums.takeIf { mode == LinkedLibraryMode.Albums }.orEmpty()
    val playlists = state.playlists.takeIf { mode == LinkedLibraryMode.Playlists }.orEmpty()
    val trackSort = if (mode == LinkedLibraryMode.Songs) linkedTrackSort(trackSortMode) else LibraryTrackSortMode.Title
    val albumSort = if (mode == LinkedLibraryMode.Albums) linkedAlbumSort(albumSortMode) else AlbumSortMode.Title
    val artistSort = if (mode == LinkedLibraryMode.Artists) artistSortMode else ArtistSortMode.Name
    val catalog by key(query) {
        produceState(
            LinkedLibraryCatalog.Empty,
            tracks, albums, playlists, remoteQuery, trackResultQuery, trackSort, albumSort, artistSort,
        ) {
            if (query.isNotEmpty() && query != remoteQuery) delay(150)
            val previousTrackOrder = value.trackOrder
            value = withContext(Dispatchers.Default) {
                LinkedLibraryCatalog.build(
                    searchText = searchText,
                    tracks = tracks,
                    playlists = playlists,
                    query = query,
                    trackResultQuery = trackResultQuery,
                    sortMode = trackSort,
                    albumSortMode = albumSort,
                    artistSortMode = artistSort,
                    includeSortedTracks = mode == LinkedLibraryMode.Songs,
                    includeAlbums = mode == LinkedLibraryMode.Albums,
                    includeArtists = mode == LinkedLibraryMode.Artists,
                    includePlaylists = mode == LinkedLibraryMode.Playlists,
                    remoteAlbums = albums,
                    randomOrder = randomOrder,
                    previousTrackOrder = previousTrackOrder,
                )
            }
        }
    }
    return catalog
}
