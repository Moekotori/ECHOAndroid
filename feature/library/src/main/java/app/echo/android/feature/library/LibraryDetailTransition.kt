package app.echo.android.feature.library

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.EchoMotion
import app.echo.android.design.EchoPageBack
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.EchoPageContent
import app.echo.android.design.rememberEchoContentMotion
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

/** Album, artist and playlist pages open from a cover card, so their cover can fly between card and hero. */
private val LibraryDetailTransitionTarget.hasSharedCover: Boolean
    get() = this is LibraryDetailTransitionTarget.AlbumDetail ||
        this is LibraryDetailTransitionTarget.ArtistDetail ||
        this is LibraryDetailTransitionTarget.PlaylistDetail ||
        this is LibraryDetailTransitionTarget.LinkedAlbum ||
        this is LibraryDetailTransitionTarget.LinkedArtist

private fun isLibraryDetailBackward(
    initial: LibraryDetailTransitionTarget,
    target: LibraryDetailTransitionTarget,
): Boolean =
    target == LibraryDetailTransitionTarget.Browser ||
        (initial is LibraryDetailTransitionTarget.AlbumDetail && target is LibraryDetailTransitionTarget.ArtistDetail) ||
        (initial is LibraryDetailTransitionTarget.LinkedAlbum && target is LibraryDetailTransitionTarget.LinkedArtist)

@Composable
internal fun LibraryDetailTransition(
    targetState: LibraryDetailTransitionTarget,
    split: Boolean,
    outerNavigationOwnsTransition: Boolean = false,
    back: EchoPageBack<LibraryDetailTransitionTarget>? = null,
    modifier: Modifier = Modifier,
    content: @Composable (LibraryDetailTransitionTarget) -> Unit,
) {
    val motion = rememberEchoContentMotion()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    // Cross-tab and split layouts keep their own movement; only the plain browser <-> detail trip flies a cover.
    val coverFlights = !split && !outerNavigationOwnsTransition && !lightweight
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
        isBackward = ::isLibraryDetailBackward,
        // Cross-tab navigation already moves the whole library page. Keep its detail
        // ready underneath, without a second slide, scale, or staggered reveal.
        fadeOnly = split || outerNavigationOwnsTransition,
        transitionSpec = if (outerNavigationOwnsTransition) {
            {
                if (initialState == LibraryDetailTransitionTarget.Browser ||
                    this.targetState == LibraryDetailTransitionTarget.Browser
                ) {
                    ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
                } else {
                    motion.pageFade()
                }
            }
        } else if (coverFlights) {
            {
                val browserToCover = initialState == LibraryDetailTransitionTarget.Browser && this.targetState.hasSharedCover
                val coverToBrowser = this.targetState == LibraryDetailTransitionTarget.Browser && initialState.hasSharedCover
                when {
                    browserToCover -> EchoMotion.pagePushCover()
                    coverToBrowser -> EchoMotion.pagePopCover()
                    isLibraryDetailBackward(initialState, this.targetState) -> motion.pagePop()
                    else -> motion.pagePush()
                }
            }
        } else null,
        sharedElements = coverFlights,
        modifier = modifier,
        back = back,
        label = "library-detail-transition",
    ) { target ->
        savedState.SaveableStateProvider(target.pageKey) { content(target) }
    }
}
