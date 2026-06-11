package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MediaStoreSampleRateFastPathTest {
    @Test
    fun unchangedTrackReusesExistingSampleRateWithoutReadingFile() {
        val existingTrack = testTrack(sampleRateHz = 48_000)
        val existingFingerprint = existingTrack.toTrackFingerprint()
        var sampleRateReads = 0

        val scannedTrack = testTrack(sampleRateHz = existingFingerprint.sampleRateHz)
            .withFastPathSampleRate(existingFingerprint) {
                sampleRateReads += 1
                96_000
            }

        assertEquals(0, sampleRateReads)
        assertEquals(48_000, scannedTrack.sampleRateHz)
        assertEquals(existingTrack.fingerprint, scannedTrack.fingerprint)
    }

    @Test
    fun changedTrackReadsSampleRateAndRebuildsFingerprint() {
        val existingTrack = testTrack(sampleRateHz = 48_000)
        val existingFingerprint = existingTrack.toTrackFingerprint()
        var sampleRateReads = 0

        val scannedTrack = testTrack(sampleRateHz = existingFingerprint.sampleRateHz, sizeBytes = 2_048L)
            .withFastPathSampleRate(existingFingerprint) {
                sampleRateReads += 1
                96_000
            }

        assertEquals(1, sampleRateReads)
        assertEquals(96_000, scannedTrack.sampleRateHz)
        assertNotEquals(existingTrack.fingerprint, scannedTrack.fingerprint)
        assertEquals(buildTrackFingerprint(scannedTrack), scannedTrack.fingerprint)
    }

    private fun LibraryTrackEntity.toTrackFingerprint(): TrackFingerprint =
        TrackFingerprint(
            id = id,
            contentUri = contentUri,
            sampleRateHz = sampleRateHz,
            fingerprint = fingerprint,
        )

    private fun testTrack(
        sampleRateHz: Int?,
        sizeBytes: Long = 1_024L,
    ): LibraryTrackEntity =
        LibraryTrackEntity(
            id = "mediastore:1",
            contentUri = "content://media/external/audio/media/1",
            title = "Track",
            artist = "Artist",
            album = "Album",
            albumArtist = "Album Artist",
            artworkUri = null,
            durationMs = 180_000L,
            trackNumber = 1,
            discNumber = 1,
            year = 2026,
            mimeType = "audio/flac",
            sizeBytes = sizeBytes,
            sampleRateHz = sampleRateHz,
            dateModifiedSeconds = 123L,
            relativePath = "Music/",
        ).withScanMetadata()
}
