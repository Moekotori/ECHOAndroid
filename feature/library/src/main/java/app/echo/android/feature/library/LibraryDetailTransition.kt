package app.echo.android.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.EchoPageContent
import app.echo.android.model.connect.EchoRemotePlaylist
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.FolderSummary
import app.echo.android.model.library.GenreSummary

/** Snapshot the outgoing collection; clearing the selection must not empty its exit frame. */
internal sealed interface LibraryDetailTransitionTarget {
    data object Browser : LibraryDetailTransitionTarget
    data class AlbumDetail(val album: AlbumSummary, val tracks: LazyPagingItems<EchoTrack>) : LibraryDetailTransitionTarget
    data class ArtistDetail(
        val artist: ArtistSummary,
        val tracks: LazyPagingItems<EchoTrack>,
        val albums: LazyPagingItems<AlbumSummary>?,
    ) : LibraryDetailTransitionTarget
    data class GenreDetail(val genre: GenreSummary, val tracks: LazyPagingItems<EchoTrack>) : LibraryDetailTransitionTarget
    data class FolderDetail(val folder: FolderSummary, val tracks: LazyPagingItems<EchoTrack>) : LibraryDetailTransitionTarget
    data class PlaylistDetail(val playlist: EchoPlaylist, val tracks: LazyPagingItems<EchoTrack>) : LibraryDetailTransitionTarget
    data class LinkedAlbum(val album: AlbumSummary) : LibraryDetailTransitionTarget
    data class LinkedArtist(val artist: ArtistSummary) : LibraryDetailTransitionTarget
    data class LinkedPlaylist(val playlist: EchoRemotePlaylist) : LibraryDetailTransitionTarget
}

private val LibraryDetailTransitionTarget.pageKey: String
    get() = when (this) {
        LibraryDetailTransitionTarget.Browser -> "library-browser"
        is LibraryDetailTransitionTarget.AlbumDetail -> "album:${album.albumKey}"
        is LibraryDetailTransitionTarget.ArtistDetail -> "artist:${artist.artistKey}"
        is LibraryDetailTransitionTarget.GenreDetail -> "genre:${genre.genreKey}"
        is LibraryDetailTransitionTarget.FolderDetail -> "folder:${folder.folderKey}"
        is LibraryDetailTransitionTarget.PlaylistDetail -> "playlist:${playlist.id}"
        is LibraryDetailTransitionTarget.LinkedAlbum -> "linked-album:${album.albumKey}"
        is LibraryDetailTransitionTarget.LinkedArtist -> "linked-artist:${artist.artistKey}"
        is LibraryDetailTransitionTarget.LinkedPlaylist -> "linked-playlist:${playlist.id}"
    }

@Composable
internal fun LibraryDetailTransition(
    targetState: LibraryDetailTransitionTarget,
    split: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (LibraryDetailTransitionTarget) -> Unit,
) {
    val savedState = rememberSaveableStateHolder()
    var retainedKeys by rememberSaveable { mutableStateOf(emptyMap<String, String>()) }
    LaunchedEffect(targetState.pageKey) {
        // Store small UI state for the browser and at most one collection of each kind.
        // No paging data or collection models enter the saved-state cache.
        val kind = targetState.pageKey.substringBefore(':')
        retainedKeys[kind]?.takeIf { it != targetState.pageKey }?.let {
            savedState.removeState(it)
        }
        retainedKeys = retainedKeys + (kind to targetState.pageKey)
    }
    EchoPageContent(
        targetState = targetState,
        contentKey = { it.pageKey },
        isBackward = { initial, target ->
            target == LibraryDetailTransitionTarget.Browser ||
                (initial is LibraryDetailTransitionTarget.AlbumDetail && target is LibraryDetailTransitionTarget.ArtistDetail) ||
                (initial is LibraryDetailTransitionTarget.LinkedAlbum && target is LibraryDetailTransitionTarget.LinkedArtist)
        },
        fadeOnly = split,
        modifier = modifier,
        label = "library-detail-transition",
    ) { target ->
        savedState.SaveableStateProvider(target.pageKey) { content(target) }
    }
}
