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
}
