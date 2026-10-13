package app.echo.android.model.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EchoLibraryLayoutTest {
    @Test fun canKeepOnlyFoldersAndCannotHideTheLastCategory() {
        val layout = EchoLibraryLayout(hidden = EchoLibraryCategory.entries.toSet() - EchoLibraryCategory.Folders)
        assertEquals(listOf(EchoLibraryCategory.Folders), layout.visibleCategories())
        assertEquals(layout, layout.withVisibility(EchoLibraryCategory.Folders, false))
        assertEquals(listOf(EchoLibraryCategory.Songs), layout.visibleCategories(listOf(EchoLibraryCategory.Songs)))
    }

    @Test fun normalizationRepairsMissingDuplicateAndAllHiddenCategories() {
        val layout = EchoLibraryLayout(listOf(EchoLibraryCategory.Folders, EchoLibraryCategory.Folders),
            EchoLibraryCategory.entries.toSet()).normalized()
        assertEquals(EchoLibraryCategory.entries.size, layout.order.size)
        assertEquals(EchoLibraryCategory.Folders, layout.order.first())
        assertEquals(listOf(EchoLibraryCategory.Folders), layout.visibleCategories())
    }

    @Test fun movingAndRestoringVisibilityPreserveOtherChoices() {
        val layout = EchoLibraryLayout(hidden = setOf(EchoLibraryCategory.Artists))
        val moved = layout.move(EchoLibraryCategory.Albums, -1)
        assertEquals(listOf(EchoLibraryCategory.Albums, EchoLibraryCategory.Songs), moved.order.take(2))
        assertEquals(layout.hidden, moved.hidden)
        assertEquals(moved, moved.move(EchoLibraryCategory.Albums, -1))
        assertFalse(EchoLibraryCategory.Artists in moved.withVisibility(EchoLibraryCategory.Artists, true).hidden)
    }
}
