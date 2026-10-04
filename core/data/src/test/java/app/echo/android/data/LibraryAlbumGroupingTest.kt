package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryAlbumGroupingTest {
    @Test
    fun slashGuestCreditsInSharedMusicFolderJoinImaginalia() {
        val tracks = imaginaliaTracks("Music/")
        val changes = LibraryAlbumGrouping.reconcile(tracks).associateBy { it.id }
        val reconciled = tracks.map { changes[it.id] ?: it }

        assertEquals(setOf("イマジナリア::森羅万象"), reconciled.map { it.albumKey }.toSet())
        assertEquals(tracks.map { it.artist }, reconciled.map { it.artist })
        assertTrue(LibraryAlbumGrouping.reconcile(reconciled).isEmpty())
    }

    @Test
    fun albumArtistCopiedFromGuestCreditsDoesNotSplitRelease() {
        val tracks = imaginaliaTracks("Music/イマジナリア/").map {
            it.copy(albumArtist = it.artist).withComputedSearchMetadata()
        }
        val changes = LibraryAlbumGrouping.reconcile(tracks).associateBy { it.id }
        val reconciled = tracks.map { changes[it.id] ?: it }

        assertEquals(setOf("イマジナリア::森羅万象"), reconciled.map { it.albumKey }.toSet())
        assertEquals(tracks.map { it.albumArtist }, reconciled.map { it.albumArtist })
        assertTrue(LibraryAlbumGrouping.reconcile(reconciled).isEmpty())
    }

    @Test
    fun explicitReleaseOwnersAndUserEditsStaySeparateDespiteCommonSongArtist() {
        val tracks = imaginaliaTracks("Music/イマジナリア/")
        val distinctOwners = tracks.mapIndexed { index, track ->
            track.copy(albumArtist = "Release owner $index").withComputedSearchMetadata()
        }
        assertTrue(LibraryAlbumGrouping.reconcile(distinctOwners).isEmpty())
        val edited = tracks.map {
            it.copy(albumArtist = it.artist, metadataEditedAtEpochMs = 1L).withComputedSearchMetadata()
        }
        assertTrue(LibraryAlbumGrouping.reconcile(edited).isEmpty())
    }

    @Test
    fun sharedFolderWithoutCommonArtistDoesNotMergeSameTitle() {
        val tracks = listOf("Queen", "ABBA").mapIndexed { index, artist ->
            track("$index", "Greatest Hits", artist, path = "Download/").withComputedSearchMetadata()
        }
        assertTrue(LibraryAlbumGrouping.reconcile(tracks).isEmpty())
    }

    @Test
    fun sharedSlashBandNameIsKeptWhole() {
        val tracks = listOf("AC/DC", "AC/DC/Guest").mapIndexed { index, artist ->
            track("$index", "Live", artist, path = "Music/Live/").withComputedSearchMetadata()
        }
        val changes = LibraryAlbumGrouping.reconcile(tracks).associateBy { it.id }
        assertEquals(setOf("live::ac/dc"), tracks.map { (changes[it.id] ?: it).albumKey }.toSet())
        assertEquals(listOf("AC/DC"), LibraryArtistPolicy.names("AC/DC"))
    }

    private fun imaginaliaTracks(path: String): List<LibraryTrackEntity> =
        listOf("森羅万象/あよ/棗いつき", "森羅万象/ななひら", "森羅万象/ちょこ", "森羅万象/nayuta")
            .mapIndexed { index, artist ->
                track("$index", "イマジナリア", artist, path = path).withComputedSearchMetadata()
            }

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
    fun issue8GuestArtistsDoNotSplitOneLocalAlbum() {
        val tracks = listOf("周杰伦", "周杰伦;潘儿", "周杰伦;费玉清").mapIndexed { index, artist ->
            track("$index", "依然范特西", artist, path = "Music/依然范特西/")
                .withComputedSearchMetadata()
        }
        val changes = LibraryAlbumGrouping.reconcile(tracks).associateBy { it.id }
        val reconciled = tracks.map { changes[it.id] ?: it }
        assertEquals(1, reconciled.map { it.albumKey }.distinct().size)
        assertEquals(tracks.map { it.artist }, reconciled.map { it.artist })
        assertTrue(LibraryAlbumGrouping.reconcile(reconciled).isEmpty())
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

    @Test
    fun incrementalGroupingLoadsSiblingsInTheSameAlbumFolderOnly() {
        val folders = LibraryAlbumGrouping.groupingFolders(
            listOf("Music/Radiohead/OK Computer/CD1", "Music/Radiohead/OK Computer/"),
        )
        assertEquals(setOf("music/radiohead/ok computer"), folders)
        val rows = listOf(
            TrackIdPathRow("keep-disc", "Music/Radiohead/OK Computer/CD2"),
            TrackIdPathRow("keep-root", "Music/Radiohead/OK Computer/"),
            TrackIdPathRow("other", "Music/Radiohead/Pablo Honey/"),
        )
        assertEquals(
            listOf("keep-disc", "keep-root"),
            LibraryAlbumGrouping.trackIdsInGroupingFolders(rows, folders),
        )
        assertTrue(LibraryAlbumGrouping.trackIdsInGroupingFolders(rows, emptySet()).isEmpty())
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
