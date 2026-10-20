package app.echo.android.data

import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackMetadataUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryMetadataPreserveTest {
    @Test
    fun explicitComposerClearAndEditedGenreSurviveRescan() {
        val current = compilationTracks().first().copy(composer = "Composer", genre = "Old genre")
        val update = EchoTrackMetadataUpdate(current.id, current.title, current.artist, current.album,
            current.albumArtist, current.trackNumber, current.discNumber, current.year, composer = "", genre = "New genre")
        val edited = current.withUserMetadata(update, 99L)
        assertEquals(null, edited.composer)
        val rescanned = current.withPreservedUserMetadata(edited)
        assertEquals(null, rescanned.composer)
        assertEquals("New genre", rescanned.genre)
    }

    @Test
    fun titleOrderArtworkAndFileStatsEditsKeepCompilationThroughRescan() {
        val tracks = compilationTracks()
        val current = tracks.first()
        val titleEdit = current.withUserMetadata(EchoTrackMetadataUpdate(
            current.id, "Edited title", current.artist, current.album, current.albumArtist,
            current.trackNumber, current.discNumber, current.year,
        ), 99L)
        val orderAndArtworkEdit = titleEdit.copy(trackNumber = 7, artworkUri = "content://art/edited").withFingerprint()
        val written = orderAndArtworkEdit.copy(sizeBytes = 4096, dateModifiedSeconds = 22).withFingerprint()
        val rescanned = current.withPreservedUserMetadata(written).withScanMetadata(10, written)
        assertEquals(current.albumKey, written.albumKey)
        assertEquals(current.albumKey, rescanned.albumKey)
        assertEquals(current.albumKey, rescanned.resolvedAlbumKey())
        assertEquals(current.albumKey, LibraryPinyinBackfillPolicy.apply(written).albumKey)
        assertEquals(current.albumKey, written.copy(source = "saf", id = "saf:0").withScanMetadata(10, written).albumKey)
        assertTrue(LibraryAlbumGrouping.reconcile(listOf(rescanned) + tracks.drop(1)).isEmpty())
    }

    @Test
    fun explicitAlbumOwnerEditAndFolderMoveDoNotKeepAnOldInferredKey() {
        val current = compilationTracks().first()
        val ownerEdit = current.withUserMetadata(EchoTrackMetadataUpdate(
            current.id, current.title, current.artist, current.album, "New owner",
            current.trackNumber, current.discNumber, current.year,
        ), 99L)
        assertEquals("compilation::new owner", ownerEdit.albumKey)
        assertNotEquals(current.albumKey, ownerEdit.albumKey)
        val artistEdit = current.withUserMetadata(EchoTrackMetadataUpdate(
            current.id, current.title, "New artist", current.album, current.albumArtist,
            current.trackNumber, current.discNumber, current.year,
        ), 99L)
        assertNotEquals(current.albumKey, artistEdit.albumKey)
        val moved = current.copy(relativePath = "Music/Other/").withScanMetadata(10, current)
        assertNotEquals(current.albumKey, moved.albumKey)
    }

    private fun compilationTracks(): List<LibraryTrackEntity> {
        val tracks = listOf("Artist A", "Artist B").mapIndexed { index, artist ->
            EchoTrack("mediastore:$index", "content://media/$index", "Song $index", artist,
                album = "Compilation", albumArtist = artist, durationMs = 1000)
                .toLibraryTrackEntity().copy(relativePath = "Music/Compilation/").withScanMetadata()
        }
        val changes = LibraryAlbumGrouping.reconcile(tracks).associateBy { it.id }
        return tracks.map { changes[it.id] ?: it }
    }

    @Test
    fun mediaStoreIncomingRowKeepsInAppTitleAndArtworkEdits() {
        val incoming = LibraryTrackEntity(
            id = "mediastore:1",
            contentUri = "content://media/external/audio/media/1",
            title = "Raw Title",
            artist = "Raw Artist",
            album = "Raw Album",
            albumArtist = "Raw Album Artist",
            artworkUri = "content://art/raw",
            durationMs = 180_000L,
            trackNumber = 1,
            discNumber = 1,
            year = 2024,
            mimeType = "audio/flac",
            sizeBytes = 1024L,
            sampleRateHz = 48_000,
            dateModifiedSeconds = 10L,
        )
        val edited = incoming.copy(
            title = "Edited Title",
            artist = "Edited Artist",
            album = "Edited Album",
            artworkUri = "content://art/edited",
            metadataEditedAtEpochMs = 99L,
        )
        val preserved = incoming.withPreservedUserMetadata(edited)
        assertEquals("Edited Title", preserved.title)
        assertEquals("Edited Artist", preserved.artist)
        assertEquals("Edited Album", preserved.album)
        assertEquals("content://art/edited", preserved.artworkUri)
        assertEquals(99L, preserved.metadataEditedAtEpochMs)
    }

    @Test
    fun remoteSubsonicResyncKeepsInAppTitleAndArtworkEdits() {
        val incoming = LibraryTrackEntity(
            id = "subsonic:demo:song:1",
            contentUri = "https://navidrome.example/rest/stream.view?id=s1",
            title = "Server Title",
            artist = "Server Artist",
            album = "Server Album",
            albumArtist = "Server Album Artist",
            artworkUri = "https://navidrome.example/rest/getCoverArt.view?id=c1",
            durationMs = 180_000L,
            trackNumber = 1,
            discNumber = 1,
            year = 2024,
            mimeType = "audio/flac",
            sizeBytes = 1024L,
            dateModifiedSeconds = 0L,
            source = "subsonic:demo",
            lastSeenScanRunId = 50L,
        ).withScanMetadata(50L)
        val edited = incoming.copy(
            title = "Edited Title",
            artworkUri = "https://edited.example/cover.jpg",
            metadataEditedAtEpochMs = 99L,
        ).withScanMetadata(50L)
        val prepared = incoming.copy(lastSeenScanRunId = 80L).prepareRemoteSyncTrack(edited)
        assertEquals("Edited Title", prepared.title)
        assertEquals("https://edited.example/cover.jpg", prepared.artworkUri)
        assertEquals(99L, prepared.metadataEditedAtEpochMs)
        assertEquals("Server Artist", prepared.artist)
        assertEquals(80L, prepared.lastSeenScanRunId)
    }

    @Test
    fun remoteResyncKeepsExistingIdWhenContentUriMatchesAnotherSourceRow() {
        val existing = LibraryTrackEntity(
            id = "subsonic:old:song:1",
            contentUri = "https://navidrome.example/rest/stream.view?id=s1",
            title = "Old",
            artist = "A",
            album = "B",
            albumArtist = "A",
            artworkUri = null,
            durationMs = 1_000L,
            trackNumber = 1,
            discNumber = 1,
            year = 2020,
            mimeType = "audio/flac",
            sizeBytes = 8L,
            dateModifiedSeconds = 0L,
            source = "subsonic:old",
        )
        val incoming = existing.copy(
            id = "subsonic:new:song:1",
            title = "New",
            source = "subsonic:new",
        )
        val remapped = remapRemoteTrackIdentity(
            incoming = incoming,
            existingByContentUri = mapOf(existing.contentUri to existing),
        )
        assertEquals("subsonic:old:song:1", remapped.id)
        assertEquals("New", remapped.title)
        assertEquals("subsonic:new", remapped.source)
        assertEquals(
            incoming.id,
            remapRemoteTrackIdentity(incoming, emptyMap()).id,
        )
    }
}
