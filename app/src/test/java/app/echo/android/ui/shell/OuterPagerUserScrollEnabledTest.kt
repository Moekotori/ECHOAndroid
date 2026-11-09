package app.echo.android.ui.shell

import androidx.compose.foundation.pager.PagerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OuterPagerUserScrollEnabledTest {
    @Test
    fun connectIsNotSnappedWhenNestedDragHasOnlyCrossedTheMidpoint() {
        val connect = EchoPagerPage.Connect.ordinal
        val inBetween = PagerState(currentPage = connect, currentPageOffsetFraction = -0.4f) {
            EchoPagerPage.entries.size
        }
        val snapped = PagerState(currentPage = connect) { EchoPagerPage.entries.size }

        assertEquals(connect, inBetween.settledPage)
        assertNull(inBetween.snappedDockPage())
        assertEquals(EchoPagerPage.Connect, snapped.snappedDockPage())
    }

    @Test
    fun pcBrowserAndDetailsLockOuterSwipeIncludingWideScreens() {
        for (split in listOf(false, true)) {
            assertFalse(
                outerPagerUserScrollEnabled(
                    libraryDetailOpen = false,
                    linkedLibraryPageOpen = true,
                    prefersLibrarySplit = split,
                    settledPage = EchoPagerPage.Library.ordinal,
                    targetPage = EchoPagerPage.Library.ordinal,
                    scrollInProgress = false,
                    innerTabPageSettled = false,
                ),
            )
        }
    }

    @Test
    fun pcPlaylistPageCannotUnlockWhileOuterScrollIsInProgress() {
        assertFalse(
            outerPagerUserScrollEnabled(
                libraryDetailOpen = false,
                linkedLibraryPageOpen = true,
                prefersLibrarySplit = false,
                settledPage = EchoPagerPage.Library.ordinal,
                targetPage = EchoPagerPage.Library.ordinal,
                scrollInProgress = true,
                innerTabPageSettled = false,
            ),
        )
    }

    @Test
    fun homeAllowsSwipe() {
        assertTrue(
            outerPagerUserScrollEnabled(
                libraryDetailOpen = false,
                prefersLibrarySplit = false,
                settledPage = EchoPagerPage.Now.ordinal,
                targetPage = EchoPagerPage.Now.ordinal,
                scrollInProgress = false,
                innerTabPageSettled = false,
            ),
        )
    }

    @Test
    fun openingAlbumFromHomeKeepsSwipeUntilLibrarySettles() {
        assertTrue(
            outerPagerUserScrollEnabled(
                libraryDetailOpen = true,
                prefersLibrarySplit = false,
                settledPage = EchoPagerPage.Now.ordinal,
                targetPage = EchoPagerPage.Library.ordinal,
                scrollInProgress = true,
                innerTabPageSettled = false,
            ),
        )
    }

    @Test
    fun libraryDetailLocksSwipeWhenSettledOnLibrary() {
        assertFalse(
            outerPagerUserScrollEnabled(
                libraryDetailOpen = true,
                prefersLibrarySplit = false,
                settledPage = EchoPagerPage.Library.ordinal,
                targetPage = EchoPagerPage.Library.ordinal,
                scrollInProgress = false,
                innerTabPageSettled = false,
            ),
        )
    }

    @Test
    fun splitLibraryKeepsSwipeWithDetailOpen() {
        assertTrue(
            outerPagerUserScrollEnabled(
                libraryDetailOpen = true,
                prefersLibrarySplit = true,
                settledPage = EchoPagerPage.Library.ordinal,
                targetPage = EchoPagerPage.Library.ordinal,
                scrollInProgress = false,
                innerTabPageSettled = false,
            ),
        )
    }

    @Test
    fun connectPageLocksOuterPager() {
        assertFalse(
            outerPagerUserScrollEnabled(
                libraryDetailOpen = false,
                prefersLibrarySplit = false,
                settledPage = EchoPagerPage.Connect.ordinal,
                targetPage = EchoPagerPage.Connect.ordinal,
                scrollInProgress = false,
                innerTabPageSettled = true,
            ),
        )
    }
}
