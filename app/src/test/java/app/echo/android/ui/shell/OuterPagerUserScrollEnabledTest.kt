package app.echo.android.ui.shell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OuterPagerUserScrollEnabledTest {
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
