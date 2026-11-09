package app.echo.android.feature.library

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.EchoMotion
import app.echo.android.design.EchoPageBack
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.EchoPageContent
import app.echo.android.design.echoPageBackgroundColor
import app.echo.android.design.rememberEchoContentMotion
import app.echo.android.model.connect.EchoRemotePlaylist
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.FolderSummary
import app.echo.android.model.library.GenreSummary
import app.echo.android.model.library.LibraryTrackSortMode

private const val DetailPopDurationMs = 240

/** Snapshot the outgoing collection; clearing the selection must not empty its exit frame. */
internal sealed interface LibraryDetailTransitionTarget {
    data object Browser : LibraryDetailTransitionTarget
    data class AlbumDetail(
        val album: AlbumSummary,
        val tracks: LazyPagingItems<EchoTrack>,
        val liked: Boolean?,
        val onOpenArtist: (() -> Unit)?,
    ) : LibraryDetailTransitionTarget
    data class ArtistDetail(
        val artist: ArtistSummary,
        val tracks: LazyPagingItems<EchoTrack>,
        val albums: LazyPagingItems<AlbumSummary>?,
        val query: String,
        val sort: LibraryTrackSortMode,
        val listState: LazyListState,
        val albumListState: LazyListState,
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
    val exitDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
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
        // Move just the covering detail. The browser keeps its viewport throughout the transition.
        fadeOnly = true,
        transitionSpec = {
            val fromBrowser = initialState == LibraryDetailTransitionTarget.Browser
            val toBrowser = this.targetState == LibraryDetailTransitionTarget.Browser
            when {
                outerNavigationOwnsTransition && fromBrowser ->
                    ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
                split || lightweight || (!fromBrowser && !toBrowser) -> motion.pageFade()
                toBrowser -> ContentTransform(
                    EnterTransition.None,
                    slideOutHorizontally(tween<IntOffset>(DetailPopDurationMs, easing = EchoMotion.SilkExit)) { width ->
                        exitDirection * width
                    } + fadeOut(tween(DetailPopDurationMs, easing = EchoMotion.SilkExit), targetAlpha = 0.85f),
                    sizeTransform = null,
                )
                else -> ContentTransform(
                    slideInHorizontally(EchoMotion.silkOffset(EchoMotion.PageExitMs)) { it },
                    ExitTransition.None,
                    sizeTransform = null,
                )
            }
        },
        retainedPage = LibraryDetailTransitionTarget.Browser.takeUnless { split },
        modifier = modifier.clipToBounds(),
        back = back,
        label = "library-detail-transition",
    ) { target ->
        savedState.SaveableStateProvider(target.pageKey) {
            if (target == LibraryDetailTransitionTarget.Browser) {
                content(target)
            } else {
                Box(Modifier.fillMaxSize().background(echoPageBackgroundColor())) { content(target) }
            }
        }
    }
}
