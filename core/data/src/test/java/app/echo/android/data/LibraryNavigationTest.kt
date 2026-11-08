package app.echo.android.data

import app.echo.android.model.library.AlbumSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryNavigationTest {
    @Test
    fun albumNavigationUsesAlbumArtistMembershipsInsteadOfSongArtist() {
        val album = AlbumSummary("album", "Release", " Ｅａｇｌｅｓ ", "Guest singer", "cover", 1, 0L, null)
        val target = albumArtistNavigationTarget(album)
        assertEquals("album-artist:eagles", target?.artistKey)
        assertEquals("cover", target?.artworkUri)
        assertEquals("album-artist:guest singer", albumArtistNavigationTarget(album.copy(albumArtist = " "))?.artistKey)
        assertEquals("album-artist:guest singer", albumArtistNavigationTarget(album.copy(albumArtist = "Unknown artist"))?.artistKey)
        assertNull(albumArtistNavigationTarget(album.copy(albumArtist = "Various Artists")))
        assertNull(albumArtistNavigationTarget(album.copy(albumArtist = null, artist = null)))
    }

    @Test
    fun artistKeyMatchesLibraryAggregation() {
        val target = artistNavigationTarget("Eagles")
        assertEquals("eagles", target?.artistKey)
        assertEquals("Eagles", target?.name)
        assertEquals("eagles", artistNavigationTarget("Ｅａｇｌｅｓ")?.artistKey)
        assertEquals("eagles", artistNavigationTarget(" Eagles  ")?.artistKey)
    }

    @Test
    fun unknownAndVariousArtistsAreNotPages() {
        assertNull(artistNavigationTarget(""))
        assertNull(artistNavigationTarget("   "))
        assertNull(artistNavigationTarget("Unknown artist"))
        assertNull(artistNavigationTarget("未知艺术家"))
        assertNull(artistNavigationTarget("<unknown>"))
        assertNull(artistNavigationTarget("Various Artists"))
        assertNull(artistNavigationTarget("群星"))
        assertNull(artistNavigationTarget("オムニバス"))
    }
}
