package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryAlbumGroupingTest {
    @Test
    fun discSuffixDoesNotSplitAlbumKey() {
        val disc1 = track(
            id = "1",
            album = "OK Computer (Disc 1)",
            artist = "Radiohead",
            path = "Music/Radiohead/OK Computer/CD1",
        ).withComputedSearchMetadata()
        val disc2 = track(
            id = "2",
            album = "OK Computer (CD2)",
            artist = "Radiohead",
            path = "Music/Radiohead/OK Computer/CD2",
        ).withComputedSearchMetadata()

        assertEquals(disc1.albumKey, disc2.albumKey)
        assertEquals("ok computer::radiohead", disc1.albumKey)
    }

    @Test
    fun missingAlbumArtistInSameFolderJoinsCompilation() {
        val oasis = track(
            id = "1",
            album = "Now 1",
            artist = "Oasis",
            path = "Music/Compilations/Now 1",
        ).withComputedSearchMetadata()
        val blur = track(
            id = "2",
            album = "Now 1",
            artist = "Blur",
            path = "Music/Compilations/Now 1",
        ).withComputedSearchMetadata()

        assertTrue(oasis.albumKey != blur.albumKey)
        val regrouped = LibraryAlbumGrouping.reconcile(listOf(oasis, blur))
        assertEquals(2, regrouped.size)
        assertEquals(regrouped[0].albumKey, regrouped[1].albumKey)
        assertEquals("now 1::various artists", regrouped[0].albumKey)
    }

    @Test
    fun partialAlbumArtistInSameFolderWins() {
        val tagged = track(
            id = "1",
            album = "Now 1",
            artist = "Oasis",
            albumArtist = "Various Artists",
            path = "Music/Compilations/Now 1",
        ).withComputedSearchMetadata()
        val untagged = track(
            id = "2",
            album = "Now 1",
            artist = "Blur",
            path = "Music/Compilations/Now 1",
        ).withComputedSearchMetadata()

        val regrouped = LibraryAlbumGrouping.reconcile(listOf(tagged, untagged))
        assertEquals(1, regrouped.size)
        assertEquals(tagged.albumKey, regrouped.single().albumKey)
        assertEquals("now 1::various artists", regrouped.single().albumKey)
    }

    @Test
    fun sameAlbumNameInDifferentFoldersStaysSplit() {
        val first = track(
            id = "1",
            album = "Greatest Hits",
            artist = "Queen",
            path = "Music/Queen/Greatest Hits",
        ).withComputedSearchMetadata()
        val second = track(
            id = "2",
            album = "Greatest Hits",
            artist = "Foo Fighters",
            path = "Music/Foo Fighters/Greatest Hits",
        ).withComputedSearchMetadata()

        assertTrue(LibraryAlbumGrouping.reconcile(listOf(first, second)).isEmpty())
        assertTrue(first.albumKey != second.albumKey)
    }

    @Test
    fun untaggedFolderDoesNotMergeUnknownAlbums() {
        val first = track(
            id = "1",
            album = null,
            artist = "A",
            path = "Download",
        ).withComputedSearchMetadata()
        val second = track(
            id = "2",
            album = null,
            artist = "B",
            path = "Download",
        ).withComputedSearchMetadata()

        assertTrue(LibraryAlbumGrouping.reconcile(listOf(first, second)).isEmpty())
    }

    @Test
    fun groupingFolderIgnoresDiscDirectory() {
        assertEquals(
            "music/radiohead/ok computer",
            LibraryAlbumGrouping.groupingFolder("Music/Radiohead/OK Computer/CD1"),
        )
        assertEquals(
            "music/radiohead/ok computer",
            LibraryAlbumGrouping.groupingFolder("Music/Radiohead/OK Computer/Disc 2"),
        )
    }

    private fun track(
        id: String,
        album: String?,
        artist: String,
        albumArtist: String? = null,
        path: String,
    ): LibraryTrackEntity =
        LibraryTrackEntity(
            id = "mediastore:$id",
            contentUri = "content://media/$id",
            title = "Track $id",
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            artworkUri = null,
            durationMs = 1000L,
            trackNumber = 1,
            discNumber = null,
            year = 1997,
            mimeType = "audio/flac",
            sizeBytes = 10L,
            dateModifiedSeconds = 1L,
            relativePath = path,
        )
}
