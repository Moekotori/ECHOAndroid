package app.echo.android.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryWallLayoutTest {
    @Test
    fun albumAndArtistWallsUseThreeColumns() {
        assertEquals(3, LibraryWallColumnCount)
    }

    @Test
    fun previewMockFillsOneRow() {
        assertEquals(LibraryWallColumnCount, LibraryWallPreviewAlbums.size)
        assertEquals(LibraryWallColumnCount, LibraryWallPreviewArtists.size)
        assertEquals(3, LibraryWallPreviewAlbums.map { it.albumKey }.distinct().size)
        assertEquals(3, LibraryWallPreviewArtists.map { it.artistKey }.distinct().size)
    }
}
