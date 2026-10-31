package app.echo.android.data

import app.echo.android.model.library.EchoTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LibraryTrackRelinkTest {
    private val candidate = EchoTrack("saf:new", "content://audio/new", "Image", "Artist", durationMs = 180_000L)
    private val cue = EchoTrack("saf:old#cue:2", "content://audio/old#echo-cue=2", "Second", "Artist",
        durationMs = 0L, clipStartMs = 60_000L).toLibraryTrackEntity()

    @Test
    fun openEndedCueKeepsItsIdentityAndDurationInsteadOfMergingIntoWholeFile() {
        // A CUE ID must also protect a zero-start track with unknown duration.
        for (start in listOf(0L, 60_000L)) {
            val plan = cue.copy(clipStartMs = start).relinkPlan(candidate)
            assertEquals("content://audio/new#echo-cue=2", plan.contentUri)
            assertNotEquals(candidate.uri, plan.contentUri)
            assertEquals("saf:new#cue:2", plan.trackId(candidate.id))
            assertEquals(0L, plan.durationMs)
        }
    }

    @Test
    fun missingCueFragmentIsRestoredAndBoundedCueRetainsItsOriginalFragment() {
        val missing = cue.copy(contentUri = "content://audio/old").relinkPlan(candidate.copy(uri = "content://audio/new#echo-cue=9"))
        assertEquals("content://audio/new#echo-cue=2", missing.contentUri)
        assertEquals("saf:new#cue:2", missing.trackId(candidate.id))

        val bounded = cue.copy(contentUri = "content://audio/old#echo-cue", clipStartMs = 0L,
            clipEndMs = 60_000L, durationMs = 60_000L).relinkPlan(candidate)
        assertEquals("content://audio/new#echo-cue", bounded.contentUri)
        assertEquals("saf:new#cue:2", bounded.trackId(candidate.id))
        assertEquals(60_000L, bounded.durationMs)
    }

    @Test
    fun wholeFileRelinkUsesReplacementIdentityAndDuration() {
        val plan = candidate.copy(id = "saf:old", uri = "content://audio/old", durationMs = 120_000L)
            .toLibraryTrackEntity().relinkPlan(candidate)
        assertEquals(candidate.uri, plan.contentUri)
        assertEquals(candidate.id, plan.trackId(candidate.id))
        assertEquals(candidate.durationMs, plan.durationMs)
    }
}
