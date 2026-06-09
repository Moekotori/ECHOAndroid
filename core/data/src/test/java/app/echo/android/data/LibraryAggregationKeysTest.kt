package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryAggregationKeysTest {
    @Test
    fun albumKeyUsesStableUnknownFallbacks() {
        assertEquals(
            "$UnknownAlbumKey::$UnknownArtistKey",
            libraryAlbumKey(
                normalizedAlbum = null,
                normalizedAlbumArtist = null,
                normalizedArtist = null,
            ),
        )
        assertEquals(
            "$UnknownAlbumKey::$UnknownArtistKey",
            libraryAlbumKey(
                normalizedAlbum = "",
                normalizedAlbumArtist = "",
                normalizedArtist = "",
            ),
        )
    }

    @Test
    fun albumKeyPrefersAlbumArtistOverTrackArtist() {
        assertEquals(
            "album::album artist",
            libraryAlbumKey(
                normalizedAlbum = "album",
                normalizedAlbumArtist = "album artist",
                normalizedArtist = "track artist",
            ),
        )
    }

    @Test
    fun artistKeyUsesStableUnknownFallback() {
        assertEquals(UnknownArtistKey, libraryArtistKey(null))
        assertEquals(UnknownArtistKey, libraryArtistKey(""))
        assertEquals("artist", libraryArtistKey("artist"))
    }
}
