package app.echo.android.data

import app.echo.android.model.library.CueSheet
import app.echo.android.model.library.CueSheetTrack
import app.echo.android.model.library.EchoTrack
import org.junit.Assert.*
import org.junit.Test

class LibraryImportRegressionTest {
    private fun track(id: String = "1", artist: String = "A", albumArtist: String? = null) =
        EchoTrack(id, "content://media/external/audio/media/$id", "Song", artist,
            album = "Greatest Hits", albumArtist = albumArtist, durationMs = 120_000,
            genre = "Rock").toLibraryTrackEntity().copy(relativePath = "Download/")

    @Test fun conflictingExplicitOwnersStaySeparateAndRepairPreviouslyMergedKeys() {
        val queen = track("1", "Queen", "Queen")
        val abba = track("2", "ABBA", "ABBA")
        assertTrue(LibraryAlbumGrouping.reconcile(listOf(queen, abba)).isEmpty())
        val repaired = LibraryAlbumGrouping.reconcile(listOf(queen, abba.copy(albumKey = queen.albumKey)))
        assertEquals(listOf(abba), repaired)
    }

    @Test fun sharedDownloadFolderDoesNotTurnUnrelatedAlbumsIntoACompilation() {
        assertTrue(LibraryAlbumGrouping.reconcile(listOf(track("1", "Queen"), track("2", "ABBA"))).isEmpty())
    }

    @Test fun songQueueRetainsAnchorBeyondItsCapacity() {
        val candidates = (1..600).map { LibraryPlaybackQueueCandidate("t$it", "mediastore") }
        val queue = LibraryPlaybackQueuePolicy.mergeAnchorIntoQueue(candidates[500], candidates, "local", 500)
        assertEquals("t501", queue.first().id)
        assertTrue(queue.size <= 500)
    }

    @Test fun guestSongsBelongToEveryCreditedArtistWithoutSplittingBandNames() {
        assertEquals(listOf("A", "B"), LibraryArtistPolicy.names("A; B; A"))
        assertEquals(listOf("AC/DC"), LibraryArtistPolicy.names("AC/DC"))
        assertEquals(listOf("Earth, Wind & Fire"), LibraryArtistPolicy.names("Earth, Wind & Fire"))
        val guest = track(artist = "A; B")
        assertEquals(setOf("a", "b"), guest.toSummaryKeySet().artistKeys)
        assertEquals(listOf("a", "b"), LibraryArtistPolicy.memberships(guest).map { it.artistKey })
        assertEquals("a", artistNavigationTarget("A; B")?.artistKey)
    }

    @Test fun unknownArtistsHaveOneCanonicalMembershipAndGenreSurvivesImport() {
        val imported = track(artist = "未知艺术家")
        assertEquals("Rock", imported.genre)
        assertEquals("rock", imported.genreKey)
        assertEquals(listOf("未知艺术家"), LibraryArtistPolicy.memberships(imported).map { it.artistKey })
    }

    @Test fun singleCueMovementRetainsItsStartAndChangedClipInvalidatesFingerprint() {
        val file = track()
        val sheet = CueSheet(fileName = "song.flac", tracks = listOf(
            CueSheetTrack(1, "Movement", startMs = 15_000, fileName = "song.flac")))
        val clip = file.splitByCue(sheet, "song.flac").single()
        assertEquals("1#cue:1", clip.id)
        assertEquals(15_000, clip.clipStartMs)
        assertEquals(120_000, clip.clipEndMs)
        assertEquals(105_000, clip.durationMs)
        val shifted = clip.copy(clipStartMs = 20_000, clipEndMs = 125_000)
        assertNotEquals(buildTrackFingerprint(clip), buildTrackFingerprint(shifted))
    }

    @Test fun normalSingleTrackCueKeepsStableWholeFileIdentity() {
        val file = track()
        val sheet = CueSheet(tracks = listOf(CueSheetTrack(1, "Song", startMs = 0)))
        val item = file.splitByCue(sheet).single()
        assertEquals(file.id, item.id)
        assertEquals(0, item.clipStartMs)
    }

    @Test fun cueForADifferentFileDoesNotSplitThisSong() {
        val file = track()
        val sheet = CueSheet(fileName = "other.flac", tracks = listOf(
            CueSheetTrack(1, "Movement", startMs = 15_000, fileName = "other.flac")))
        assertEquals(listOf(file), file.splitByCue(sheet, "song.flac"))
    }

    @Test fun creditedAlbumArtistOrderDoesNotSplitAnAlbum() {
        assertEquals(track(albumArtist = "A; B").albumKey, track(albumArtist = "B; A").albumKey)
    }

    @Test fun genreAndComposerOnlyUpdatesChangeTheStoredFingerprint() {
        val file = track()
        assertNotEquals(buildTrackFingerprint(file), buildTrackFingerprint(file.copy(genre = "Jazz")))
        assertNotEquals(buildTrackFingerprint(file), buildTrackFingerprint(file.copy(composer = "Composer")))
    }

    @Test fun m3uMatchesRealFileNameAndRejectsAmbiguousFallbacks() {
        val first = M3uMatchRow("1", "Song", "A", "Music/One/", "content://media/external/audio/media/123", "song.flac")
        val second = first.copy(id = "2", relativePath = "Music/Two/", contentUri = "content://media/external/audio/media/124")
        assertEquals("1", M3uPlaylistCodec.matchTrackId(M3uEntry("Music/One/song.flac"), listOf(first, second)))
        assertNull(M3uPlaylistCodec.matchTrackId(M3uEntry("song.flac"), listOf(first, second)))
        assertNull(M3uPlaylistCodec.matchTrackId(M3uEntry("missing.flac", "A - Song"), listOf(first, second)))
        assertEquals("1", M3uPlaylistCodec.matchTrackId(M3uEntry(first.contentUri), listOf(first, second)))
        assertEquals("Music/One/song.flac", first.fileLocation())
    }
}
