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

    @Test
    fun localTrackSummaryKeysStayUnprefixed() {
        val keys = LibraryTrackEntity(
            id = "mediastore:1",
            contentUri = "content://media/external/audio/media/1",
            title = "Track",
            artist = "Artist",
            album = "Album",
            albumArtist = null,
            artworkUri = null,
            durationMs = 1L,
            trackNumber = 1,
            discNumber = 1,
            year = 2026,
            mimeType = "audio/flac",
            sizeBytes = 10L,
            dateModifiedSeconds = 1L,
            relativePath = "Music/Album",
            albumKey = "album::artist",
            artistKey = "artist",
        ).toSummaryKeySet()
        assertEquals(setOf("album::artist"), keys.albumKeys)
        assertEquals(setOf("artist"), keys.artistKeys)
        assertEquals(setOf("Music/Album"), keys.folderKeys)
    }

    @Test
    fun remoteTrackSummaryKeysUsePrefixedAlbumAndSkipFolder() {
        val keys = LibraryTrackEntity(
            id = "subsonic:1",
            contentUri = "https://example/stream",
            title = "Track",
            artist = "Artist",
            album = "Album",
            albumArtist = null,
            artworkUri = null,
            durationMs = 1L,
            trackNumber = 1,
            discNumber = 1,
            year = 2026,
            mimeType = "audio/mpeg",
            sizeBytes = 10L,
            dateModifiedSeconds = 1L,
            source = "subsonic:demo",
            albumKey = "album::artist",
            artistKey = "artist",
        ).toSummaryKeySet()
        assertEquals(setOf("remote||subsonic:demo||album::artist"), keys.albumKeys)
        assertEquals(emptySet<String>(), keys.artistKeys)
        assertEquals(emptySet<String>(), keys.folderKeys)
    }
}
