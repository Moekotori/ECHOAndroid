package app.echo.android.model.library

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderSummaryTest {
    @Test
    fun browseTitleUsesTheLastPathSegment() {
        assertEquals("A", folder("Music/A").browseTitle("Unknown path"))
        assertEquals("A", folder("/Music/A/").browseTitle("Unknown path"))
        assertEquals("Unknown path", folder("").browseTitle("Unknown path"))
        assertEquals("Unknown path", folder(null).browseTitle("Unknown path"))
        assertEquals("Unknown path", folder("/").browseTitle("Unknown path"))
    }

    private fun folder(path: String?) = FolderSummary(
        folderKey = path.orEmpty(),
        path = path,
        artworkUri = null,
        trackCount = 0,
        albumCount = 0,
        artistCount = 0,
        durationMs = 0L,
        totalSizeBytes = 0L,
        latestModifiedSeconds = 0L,
    )
}
