package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun unknownSentinelsShareAlbumAndArtistKeys() {
        val album = "夜曲".normalizedForSearch()
        assertEquals(
            libraryAlbumKey(album, null, "未知艺术家".normalizedForSearch()),
            libraryAlbumKey(album, null, "Unknown artist".normalizedForSearch()),
        )
        assertEquals(
            libraryAlbumKey(album, null, "未知艺术家".normalizedForSearch()),
            libraryAlbumKey(album, null, "<unknown>".normalizedForSearch()),
        )
        assertEquals(UnknownArtistKey, libraryArtistKey("Unknown artist".normalizedForSearch()))
        assertEquals(UnknownArtistKey, libraryArtistKey("<unknown>".normalizedForSearch()))
    }

    @Test
    fun variousArtistsAliasesShareAlbumKey() {
        val album = "now that's what i call music".normalizedForSearch()
        assertEquals(
            libraryAlbumKey(album, "Various Artists".normalizedForSearch(), "oasis".normalizedForSearch()),
            libraryAlbumKey(album, "群星".normalizedForSearch(), "spice girls".normalizedForSearch()),
        )
    }

    @Test
    fun extraWhitespaceDoesNotSplitAlbumOrArtist() {
        assertEquals(
            "hotel california::eagles",
            libraryAlbumKey("Hotel  California".normalizedForSearch(), null, " Eagles ".normalizedForSearch()),
        )
        assertEquals("eagles", libraryArtistKey("Eagles".normalizedForSearch()))
        assertEquals("eagles", libraryArtistKey("Eagles  ".normalizedForSearch()))
        assertEquals("eagles", libraryArtistKey("Ｅａｇｌｅｓ".normalizedForSearch()))
    }

    @Test
    fun missingAlbumArtistStillSplitsCompilationByTrackArtist() {
        val album = "compilation".normalizedForSearch()
        assertTrue(
            libraryAlbumKey(album, null, "oasis".normalizedForSearch()) !=
                libraryAlbumKey(album, null, "blur".normalizedForSearch()),
        )
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
