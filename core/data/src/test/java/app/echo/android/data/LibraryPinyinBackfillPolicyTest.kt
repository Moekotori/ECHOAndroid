package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryPinyinBackfillPolicyTest {
    @Test
    fun chinesePlaceholderGetsDistinctPinyinAndLeavesQuery() {
        val track = placeholderTrack(
            id = "cn",
            title = "\u7d2b\u8587",
            artist = "\u743c\u7476",
            album = "\u6211\u5f88\u5fd9",
        )
        assertTrue(LibraryPinyinBackfillPolicy.matchesBackfillQuery(track))

        val updated = LibraryPinyinBackfillPolicy.apply(track)

        assertEquals("ziwei zw", updated.pinyinTitle)
        assertTrue(updated.pinyinArtist.orEmpty().startsWith("qiongyao"))
        assertFalse(LibraryPinyinBackfillPolicy.matchesBackfillQuery(updated))
        assertEquals(updated, LibraryPinyinBackfillPolicy.apply(updated))
    }

    @Test
    fun japanesePlaceholderLeavesQueryWithoutLooping() {
        val track = placeholderTrack(
            id = "jp",
            title = "\u30a2\u30a4\u30c9\u30eb",
            artist = "YOASOBI",
            album = "THE BOOK",
        )
        assertTrue(LibraryPinyinBackfillPolicy.matchesBackfillQuery(track))

        val updated = LibraryPinyinBackfillPolicy.apply(track)

        assertFalse(LibraryPinyinBackfillPolicy.matchesBackfillQuery(updated))
        assertEquals(updated, LibraryPinyinBackfillPolicy.apply(updated))
    }

    @Test
    fun accentedLatinPlaceholderLeavesQueryWithoutLooping() {
        val track = placeholderTrack(
            id = "fr",
            title = "H\u00e9l\u00e8ne",
            artist = "Roch Voisine",
            album = "Helene",
        )
        assertTrue(LibraryPinyinBackfillPolicy.matchesBackfillQuery(track))

        val updated = LibraryPinyinBackfillPolicy.apply(track)

        assertFalse(LibraryPinyinBackfillPolicy.matchesBackfillQuery(updated))
        assertEquals(updated, LibraryPinyinBackfillPolicy.apply(updated))
    }

    @Test
    fun asciiArtistStaysUntouchedWhenOnlyTitleNeedsBackfill() {
        val track = placeholderTrack(
            id = "mix",
            title = "\u9752\u82b1\u74f7",
            artist = "Jay Chou",
            album = "The Best",
        )

        val updated = LibraryPinyinBackfillPolicy.apply(track)

        assertEquals("jay chou", updated.pinyinArtist)
        assertEquals("qinghuaci qhc", updated.pinyinTitle)
        assertFalse(LibraryPinyinBackfillPolicy.matchesBackfillQuery(updated))
    }

    private fun placeholderTrack(
        id: String,
        title: String,
        artist: String,
        album: String?,
    ): LibraryTrackEntity {
        val normalizedTitle = title.normalizedForSearch()
        val normalizedArtist = artist.normalizedForSearch()
        val normalizedAlbum = album?.normalizedForSearch()
        return LibraryTrackEntity(
            id = id,
            contentUri = "content://track/$id",
            title = title,
            artist = artist,
            album = album,
            albumArtist = null,
            artworkUri = null,
            durationMs = 180_000L,
            trackNumber = 1,
            discNumber = null,
            year = 2007,
            mimeType = "audio/flac",
            sizeBytes = 1024L,
            dateModifiedSeconds = 1L,
            normalizedTitle = normalizedTitle,
            normalizedArtist = normalizedArtist,
            normalizedAlbum = normalizedAlbum,
            pinyinTitle = normalizedTitle,
            pinyinArtist = normalizedArtist,
            pinyinAlbum = normalizedAlbum,
            albumKey = "album",
            artistKey = "artist",
        )
    }
}
