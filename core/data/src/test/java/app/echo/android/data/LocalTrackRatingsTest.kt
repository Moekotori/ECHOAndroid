package app.echo.android.data

import org.junit.Assert.*
import org.junit.Test

class LocalTrackRatingsTest {
    private fun row() = LibraryTrackEntity("song", "content://audio/1", "Song", "Artist", null, null, null,
        1000, null, null, null, "audio/mpeg", 1024, dateModifiedSeconds = 123)

    @Test fun commonTagRatingsAreReadWithoutWritingAudio() {
        assertEquals(5, AudioTagRating.popm(byteArrayOf(0, -1)))
        assertEquals(3, AudioTagRating.popm(byteArrayOf(0, -128)))
        assertNull(AudioTagRating.popm(byteArrayOf(1, 2)))
        assertEquals(4, AudioTagRating.fields(mapOf("FMPS_RATING" to "0.8")))
        assertEquals(4, AudioTagRating.fields(mapOf("RATING" to "80")))
        assertNull(AudioTagRating.fields(mapOf("RATING" to "NaN")))
    }

    @Test fun rescanningPreservesLocalOverrideIncludingExplicitUnrated() {
        val incoming = row().copy(embeddedRating = 5, rating = 5)
        assertEquals(2, incoming.withPreservedRating(row().copy(localRating = 2)).rating)
        assertEquals(0, incoming.withPreservedRating(row().copy(localRating = 0)).rating)
        assertEquals(5, incoming.withPreservedRating(row().copy(localRating = null)).rating)
        assertEquals(4, row().withPreservedRating(row().copy(embeddedRating = 4)).rating)
    }

    @Test fun oldLibraryRowsReadTagsOnceAndWriteTheirNewRating() {
        val old = TrackFingerprint("song", "uri", null, "unchanged", 1024, 123, embeddedRating = null)
        assertFalse(LibraryScanPolicy.isMediaStoreRowUnchanged(old, 123, 1024))
        assertTrue(LibraryScanPolicy.isMediaStoreRowUnchanged(old.copy(embeddedRating = 0), 123, 1024))
        assertEquals(LibraryScanRowAction.Update, LibraryScanPolicy.scanRowAction("fp", "fp", null, 4))
        assertEquals(LibraryScanRowAction.RememberSeen, LibraryScanPolicy.scanRowAction("fp", "fp", 4, 4))
    }
}
