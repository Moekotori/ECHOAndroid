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
import app.echo.android.design.LocalEchoPageInteractive
import app.echo.android.design.rememberContentPagerNestedScroll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs

internal fun libraryPagerModes(
    cloudOnly: Boolean,
    layout: EchoLibraryLayout = EchoLibraryLayout(),
): List<LibraryViewMode> = if (cloudOnly) {
    listOf(LibraryViewMode.Albums)
} else {
    layout.visibleCategories().map { it.viewMode() } + LibraryViewMode.Radio
}

internal fun EchoLibraryCategory.viewMode(): LibraryViewMode = when (this) {
    EchoLibraryCategory.Songs -> LibraryViewMode.Songs
    EchoLibraryCategory.Albums -> LibraryViewMode.Albums
    EchoLibraryCategory.Artists -> LibraryViewMode.Artists
    EchoLibraryCategory.Folders -> LibraryViewMode.Folders
    EchoLibraryCategory.Playlists -> LibraryViewMode.Playlists
}

/** Category paging stays in the library; each lazy page retains its saved scroll state. */
@Composable
internal fun <Mode : Enum<Mode>> LibraryCategoryPager(
    pager: PagerState,
    modes: List<Mode>,
    selectedMode: Mode,
    onSelectMode: (Mode) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Mode) -> Unit,
) = key(modes) {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val interactive = LocalEchoPageInteractive.current
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
    LaunchedEffect(pager, selectedMode, interactive) {
        if (!interactive) return@LaunchedEffect
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
        var scrolled = false
        snapshotFlow { if (pager.isScrollInProgress) null else pager.settledPage }.collect { page ->
            if (page == null) {
                scrolled = true
            } else {
                val mode = modes[page]
                if (mode != selection) {
                    if (scrolled && abs(pager.currentPageOffsetFraction) < 0.001f) {
                        onSelect(mode)
                    } else {
                        // A layout pass during a detail transition can move the idle pager.
                        pager.requestScrollToPage(modes.indexOf(selection).coerceAtLeast(0))
                    }
                }
                scrolled = false
            }
        }
    }

    HorizontalPager(
        state = pager,
        modifier = modifier,
        key = { modes[it].name },
        beyondViewportPageCount = 0,
        userScrollEnabled = interactive && modes.size > 1,
        flingBehavior = fling,
        pageNestedScrollConnection = nestedScroll,
    ) { page -> content(modes[page]) }
}
