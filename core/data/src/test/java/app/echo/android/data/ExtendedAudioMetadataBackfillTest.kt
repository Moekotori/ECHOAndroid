package app.echo.android.data

import org.junit.Assert.*
import org.junit.Test

class ExtendedAudioMetadataBackfillTest {
    @Test fun repairFillsMetadataAndKeepsScanOwnership() {
        val track = track()
        val tags = AudioTagFields("Song", "Artist", "Album", null, 2, null, 2026)
        val next = track.withExtendedAudioMetadata(LocalAudioContainerInfo(250, 48000, tags)) { null }
        assertEquals("Song", next.title)
        assertEquals(250L, next.durationMs)
        assertEquals(48000, next.sampleRateHz)
        assertEquals(track.lastSeenScanRunId, next.lastSeenScanRunId)
        assertEquals(track.contentUri, next.contentUri)
        val unknown = next.withExtendedAudioMetadata(LocalAudioContainerInfo(0, null)) { null }
        assertEquals(next.durationMs, unknown.durationMs)
        assertEquals(next.sampleRateHz, unknown.sampleRateHz)
    }

    @Test fun manualEditsAndCueSegmentsAreNeverReplaced() {
        val info = LocalAudioContainerInfo(250, 48000, AudioTagFields("Other", "Other", null, null, null, null, null))
        for (track in listOf(track().copy(metadataEditedAtEpochMs = 1), track().copy(clipStartMs = 20))) {
            assertSame(track, track.withExtendedAudioMetadata(info) { error("Must not persist artwork") })
        }
    }

    private fun track() = LibraryTrackEntity(
        id = "saf:ape", contentUri = "content://files/1", title = "filename", artist = "Unknown",
        album = null, albumArtist = null, artworkUri = null, durationMs = 0,
        trackNumber = null, discNumber = null, year = null, mimeType = "audio/ape",
        sizeBytes = 10000, dateModifiedSeconds = 1, source = "saf", lastSeenScanRunId = 42,
    )
}
