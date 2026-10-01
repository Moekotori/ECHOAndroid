package app.echo.android.data

import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibrarySource
import org.junit.Assert.*
import org.junit.Test

class LibraryRepairArchiveCodecTest {
    @Test fun missingCueRetainsItsUserMetadataAndClipWindow() {
        val original = EchoTrack("saf:album#cue:2", "file:///album.flac#echo-cue", "Movement", "Artist",
            album = "Suite", albumArtist = "Composer", durationMs = 5000, genre = "Classical", composer = "Composer",
            source = LibrarySource.Saf, clipStartMs = 10000, clipEndMs = 15000, sampleRateHz = 96000)
            .toLibraryTrackEntity().copy(relativePath = "Music/Suite/", metadataEditedAtEpochMs = 1234, fileName = "album.flac")
        val restored = decodeRepairTrack(LibraryRepairArchiveEntity(original.id, encodeRepairTrack(original).toString(), 12345))
        assertEquals(original.toEchoTrack(), restored.toEchoTrack())
        assertEquals(original.relativePath, restored.relativePath)
        assertEquals(original.metadataEditedAtEpochMs, restored.metadataEditedAtEpochMs)
        assertEquals(original.fileName, restored.fileName)
    }
}
