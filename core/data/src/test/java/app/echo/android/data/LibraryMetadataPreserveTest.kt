package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryMetadataPreserveTest {
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
}
