package app.echo.android.feature.library

import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import app.echo.android.model.settings.EchoLibraryCategory
import app.echo.android.model.settings.EchoLibraryLayout
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.rememberContentPagerNestedScroll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.abs

internal fun libraryPagerModes(
    cloudOnly: Boolean,
    layout: EchoLibraryLayout = EchoLibraryLayout(),
): List<LibraryViewMode> = if (cloudOnly) listOf(LibraryViewMode.Albums)
    else layout.visibleCategories().map { it.viewMode() }

internal fun EchoLibraryCategory.viewMode(): LibraryViewMode = when (this) {
    EchoLibraryCategory.Songs -> LibraryViewMode.Songs
    EchoLibraryCategory.Albums -> LibraryViewMode.Albums
    EchoLibraryCategory.Artists -> LibraryViewMode.Artists
    EchoLibraryCategory.Folders -> LibraryViewMode.Folders
    EchoLibraryCategory.Playlists -> LibraryViewMode.Playlists
}

/** Category paging stays in the library; each lazy page retains its saved scroll state. */
@Composable
internal fun LibraryCategoryPager(
    pager: PagerState,
    modes: List<LibraryViewMode>,
    selectedMode: LibraryViewMode,
    onSelectMode: (LibraryViewMode) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (LibraryViewMode) -> Unit,
) = key(modes) {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val settleSpec = tween<Float>(if (lightweight) 100 else 180, easing = EchoMotion.Silk)
    val fling = PagerDefaults.flingBehavior(
        state = pager,
        snapAnimationSpec = settleSpec,
        snapPositionalThreshold = 0.2f,
    )
    val nestedScroll = rememberContentPagerNestedScroll(pager, fling)
    val selection by rememberUpdatedState(selectedMode)
    val onSelect by rememberUpdatedState(onSelectMode)

    // A tab click redirects the pager. Observe touch navigation only after that
    // request, so an old settled page cannot overwrite a newly clicked tab.
    LaunchedEffect(pager, selectedMode) {
        val requestedPage = modes.indexOf(selectedMode).coerceAtLeast(0)
        if (pager.currentPage != requestedPage || pager.currentPageOffsetFraction != 0f) {
            try {
                // Distant tab clicks should not sweep through unrelated categories.
                if (lightweight || abs(requestedPage - pager.currentPage) > 1) {
                    pager.scrollToPage(requestedPage)
                } else {
                    pager.animateScrollToPage(requestedPage, animationSpec = settleSpec)
                }
            } catch (cancelled: CancellationException) {
                // Touch can interrupt the animation; composition cancellation still propagates.
                currentCoroutineContext().ensureActive()
            }
        }
        snapshotFlow {
            if (!pager.isScrollInProgress && abs(pager.currentPageOffsetFraction) < 0.001f) {
                pager.settledPage
            } else null
        }.filterNotNull().distinctUntilChanged().collect { page ->
            val mode = modes[page]
            if (mode != selection) onSelect(mode)
        }
    }

    HorizontalPager(
        state = pager,
        modifier = modifier,
        key = { modes[it].name },
        beyondViewportPageCount = 0,
        userScrollEnabled = modes.size > 1,
        flingBehavior = fling,
        pageNestedScrollConnection = nestedScroll,
    ) { page -> content(modes[page]) }
}
